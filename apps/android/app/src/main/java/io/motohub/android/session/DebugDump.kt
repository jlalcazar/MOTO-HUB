// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Jose Luis Alcazar and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.session

import android.content.ContentValues
import android.content.Context
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
 * written to two places a cable or a file manager reaches:
 *
 * - [writeLatest] keeps one file, always the newest, in the app's external files directory. It is
 *   rewritten whenever a session ends or the app leaves the foreground, so it is there even when
 *   nobody asked for it. Read it with
 *   `adb pull /sdcard/Android/data/<application id>/files/debug/MotoVisor-debug-latest.txt`.
 * - [saveToDownloads] writes a timestamped copy to `Download/MotoVisor`, which the phone's own
 *   Files app shows, on request from Settings > Diagnostics.
 *
 * The text is [ProjectionEventLog.exportText], so it is redacted exactly as a shared log is, with
 * the settings that shape a stream added on top. Nothing is written while logging is switched off.
 */
object DebugDump {
    const val DOWNLOADS_FOLDER = "MotoVisor"
    private const val LATEST_FILE_NAME = "MotoVisor-debug-latest.txt"
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
    fun writeLatest(context: Context) {
        val appContext = context.applicationContext
        if (!MotoHubSettings.loggingEnabled(appContext)) return
        writer.execute {
            runCatching {
                val directory = File(appContext.getExternalFilesDir(null) ?: return@execute, "debug")
                directory.mkdirs()
                // Written beside the target and renamed over it, so a pull that lands mid-write
                // gets the previous complete file rather than half of the new one.
                val pending = File(directory, "$LATEST_FILE_NAME.tmp")
                pending.writeText(text(appContext), Charsets.UTF_8)
                if (!pending.renameTo(File(directory, LATEST_FILE_NAME))) pending.delete()
            }.onFailure { failure ->
                ProjectionEventLog.debug("LOG", "Debug file not written: ${failure.message}")
            }
        }
    }

    /**
     * Saves a timestamped copy under `Download/MotoVisor`. Blocking; call it off the main thread.
     *
     * @return the path as the rider will see it, relative to the phone's shared storage.
     */
    fun saveToDownloads(context: Context, nowMillis: Long = System.currentTimeMillis()): Result<String> =
        runCatching {
            val appContext = context.applicationContext
            val name = fileName(nowMillis)
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_FOLDER"
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Android did not create the file."
            }
            try {
                checkNotNull(resolver.openOutputStream(uri)) { "Android did not open the file." }
                    .use { it.write(text(appContext).toByteArray(Charsets.UTF_8)) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (failure: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
            "$relativePath/$name"
        }
}
