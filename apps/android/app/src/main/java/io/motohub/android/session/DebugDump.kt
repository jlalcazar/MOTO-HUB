// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Jose Luis Alcazar and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.session

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.motohub.android.feature.settings.MotoHubSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Puts the diagnostic log where it can be picked up without the app's help.
 *
 * The log already lives in the app's private storage and can be shared from the log screen, but
 * both need a working app and a rider with a free hand. A session that went wrong on the road is
 * reviewed later, at a desk, often after the app has been reinstalled. So the same text is also
 * written, by itself, to `Download/MotoVisor` - a folder the phone's own Files app shows and a USB
 * cable reaches:
 *
 * - `MotoVisor-debug-latest.txt` is rewritten whenever a session ends or the app leaves the
 *   foreground ([writeLatest]). It is always the newest state of the log.
 * - `MotoVisor-debug-<date>-<time>.txt` is added when a session ends ([onSessionEnded]), because
 *   the log is a ring and a later session pushes an earlier one out of it. Only the newest
 *   [KEPT_SESSION_FILES] are kept.
 *
 * The latest file is also kept in the app's external files directory, which survives a full
 * Download folder and needs no MediaStore:
 * `adb pull /sdcard/Android/data/<application id>/files/debug/MotoVisor-debug-latest.txt`.
 *
 * The text is [ProjectionEventLog.exportText], so it is redacted exactly as a shared log is, with
 * the settings that shape a stream added on top. Nothing is written while logging is switched
 * off, and the automatic copies stop when "Save debug files automatically" is turned off.
 */
object DebugDump {
    const val DOWNLOADS_FOLDER = "MotoVisor"
    const val KEPT_SESSION_FILES = 10
    private const val LATEST_FILE_NAME = "MotoVisor-debug-latest.txt"
    private const val SESSION_FILE_PREFIX = "MotoVisor-debug-2"
    private val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_FOLDER"
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MotoHubDebugDump").apply { isDaemon = true }
    }

    internal fun fileName(nowMillis: Long): String =
        "MotoVisor-debug-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMillis)) + ".txt"

    /** The settings block, as "name: value" lines. Pure, so a test can read what a dump will say. */
    internal fun settingsBlock(settings: Map<String, String>): String = buildString {
        appendLine("Settings")
        settings.forEach { (name, value) -> appendLine("  $name: $value") }
        appendLine("----------------------------------------")
    }

    /**
     * Which session files to delete so that only the newest [keep] remain. The names sort by time
     * because the timestamp in them is written most-significant first. Pure, for a test.
     */
    internal fun <T> surplus(files: List<Pair<String, T>>, keep: Int = KEPT_SESSION_FILES): List<T> =
        files.sortedByDescending { it.first }.drop(keep).map { it.second }

    fun text(context: Context): String {
        val appContext = context.applicationContext
        val settings = linkedMapOf(
            "videoQuality" to MotoHubSettings.videoQuality(appContext).name,
            "powerMode" to MotoHubSettings.videoPowerMode(appContext).name,
            "androidAutoResolution" to MotoHubSettings.androidAutoResolution(appContext).name,
            "androidAutoDensity" to MotoHubSettings.androidAutoDensity(appContext).name,
            "disableTouchscreen" to MotoHubSettings.disableTouchscreen(appContext).toString(),
            "autoConnect" to MotoHubSettings.autoConnect(appContext).toString(),
            "autoRecovery" to MotoHubSettings.autoRecovery(appContext).toString(),
            "seamlessResume" to MotoHubSettings.seamlessResume(appContext).toString(),
            "keepScreenOn" to MotoHubSettings.keepScreenOn(appContext).toString(),
            "verboseTBoxLogging" to MotoHubSettings.verboseTBoxLogging(appContext).toString()
        )
        return settingsBlock(settings) + ProjectionEventLog.exportText()
    }

    /**
     * Rewrites the always-current file, off the calling thread. Safe to call from a lifecycle
     * callback: a failure is logged and otherwise ignored, because a debug aid must never be the
     * reason a session teardown goes wrong.
     */
    fun writeLatest(context: Context) = automatic(context, sessionEnded = false)

    /** [writeLatest], plus a timestamped copy of the session that just ended. */
    fun onSessionEnded(context: Context) = automatic(context, sessionEnded = true)

    private fun automatic(context: Context, sessionEnded: Boolean) {
        val appContext = context.applicationContext
        if (!MotoHubSettings.loggingEnabled(appContext)) return
        if (!MotoHubSettings.autoDebugFiles(appContext)) return
        writer.execute {
            val text = runCatching { text(appContext) }.getOrElse { return@execute }
            report("app storage") { writeToAppStorage(appContext, text) }
            report("Download/$DOWNLOADS_FOLDER") { writeToDownloads(appContext, LATEST_FILE_NAME, text) }
            if (sessionEnded) {
                report("Download/$DOWNLOADS_FOLDER") {
                    writeToDownloads(appContext, fileName(System.currentTimeMillis()), text)
                    pruneSessionFiles(appContext)
                }
            }
        }
    }

    private inline fun report(where: String, write: () -> Unit) {
        runCatching(write).onFailure { failure ->
            ProjectionEventLog.debug("LOG", "Debug file not written to $where: ${failure.message}")
        }
    }

    private fun writeToAppStorage(context: Context, text: String) {
        val directory = File(context.getExternalFilesDir(null) ?: return, "debug")
        directory.mkdirs()
        // Written beside the target and renamed over it, so a pull that lands mid-write gets the
        // previous complete file rather than half of the new one.
        val pending = File(directory, "$LATEST_FILE_NAME.tmp")
        pending.writeText(text, Charsets.UTF_8)
        if (!pending.renameTo(File(directory, LATEST_FILE_NAME))) pending.delete()
    }

    /**
     * Saves a timestamped copy under `Download/MotoVisor` on request. Blocking; call it off the
     * main thread.
     *
     * @return the path as the rider will see it, relative to the phone's shared storage.
     */
    fun saveToDownloads(context: Context, nowMillis: Long = System.currentTimeMillis()): Result<String> =
        runCatching {
            val appContext = context.applicationContext
            val name = fileName(nowMillis)
            writeToDownloads(appContext, name, text(appContext))
            "$relativePath/$name"
        }

    /**
     * Writes [name] in the Downloads folder, replacing this app's earlier file of that name.
     *
     * Only files this install created are visible to it without a storage permission, which is
     * all that is needed: after a reinstall the old "latest" is somebody else's file, Android
     * names the new one "... (1)", and nothing is lost.
     */
    private fun writeToDownloads(context: Context, name: String, text: String) {
        val resolver = context.contentResolver
        val existing = ownedFiles(context, "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(name))
            .firstOrNull()?.second
        val uri = existing ?: checkNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                }
            )
        ) { "Android did not create the file." }
        try {
            // "wt": truncate, or a shorter log would leave the tail of the longer one behind it.
            checkNotNull(resolver.openOutputStream(uri, "wt")) { "Android did not open the file." }
                .use { it.write(text.toByteArray(Charsets.UTF_8)) }
        } catch (failure: Throwable) {
            if (existing == null) runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    }

    private fun pruneSessionFiles(context: Context) {
        val sessionFiles = ownedFiles(
            context,
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("$SESSION_FILE_PREFIX%")
        )
        surplus(sessionFiles).forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    /** This install's files in `Download/MotoVisor` matching [selection], as name to Uri. */
    private fun ownedFiles(context: Context, selection: String, arguments: Array<String>): List<Pair<String, Uri>> {
        val found = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ($selection)",
            arrayOf("$relativePath/") + arguments,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                found += cursor.getString(1) to
                    ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            }
        }
        return found
    }
}
