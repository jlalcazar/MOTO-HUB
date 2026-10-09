// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Jose Luis Alcazar and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.encoding

import java.util.Locale

/**
 * What one streaming session cost the phone, for the log.
 *
 * The power modes and the automatic savings change how fast frames are sent; whether that changes
 * how fast the battery empties or how hot the phone runs was a guess until something wrote the
 * numbers down next to each other. This collects them from the same five-second tick the adaptive
 * controller runs on and turns them into one line when the session ends.
 *
 * Pure: it is handed readings and a clock value, so a test can replay a session.
 */
class SessionPowerStats {
    private var startedAtElapsed = 0L
    private var lastSampleAtElapsed = 0L
    private var samples = 0
    private var firstBatteryPercent: Int? = null
    private var lastBatteryPercent: Int? = null
    private var chargingSamples = 0
    private var maxThermalStatus = 0
    private var frameRateSum = 0L
    private var bitrateSum = 0L
    private var savingSamples = 0

    /**
     * @param batteryPercent null when the phone does not report a level.
     * @param saving whether Battery Saver or a low battery was pacing the stream on this tick.
     */
    fun sample(
        nowElapsed: Long,
        batteryPercent: Int?,
        charging: Boolean,
        thermalStatus: Int,
        frameRate: Int,
        bitrate: Int,
        saving: Boolean
    ) {
        if (samples == 0) startedAtElapsed = nowElapsed
        lastSampleAtElapsed = nowElapsed
        samples += 1
        if (batteryPercent != null) {
            if (firstBatteryPercent == null) firstBatteryPercent = batteryPercent
            lastBatteryPercent = batteryPercent
        }
        if (charging) chargingSamples += 1
        if (thermalStatus > maxThermalStatus) maxThermalStatus = thermalStatus
        frameRateSum += frameRate
        bitrateSum += bitrate
        if (saving) savingSamples += 1
    }

    fun reset() {
        startedAtElapsed = 0L
        lastSampleAtElapsed = 0L
        samples = 0
        firstBatteryPercent = null
        lastBatteryPercent = null
        chargingSamples = 0
        maxThermalStatus = 0
        frameRateSum = 0L
        bitrateSum = 0L
        savingSamples = 0
    }

    /**
     * One line describing the session so far, or null when nothing was sampled.
     *
     * The drain rate is given only for a session that never charged and lasted long enough for a
     * one-point step of the gauge not to dominate it: 3% in five minutes reads as 36%/h, and so
     * does 1% that happened to tick over twice.
     */
    fun summary(powerModeLabel: String, thermalLabel: (Int) -> String): String? {
        if (samples == 0) return null
        val durationMillis = (lastSampleAtElapsed - startedAtElapsed).coerceAtLeast(0L)
        val first = firstBatteryPercent
        val last = lastBatteryPercent
        val battery = if (first == null || last == null) {
            "battery=unknown"
        } else {
            val delta = last - first
            val rate = if (chargingSamples == 0 && durationMillis >= MIN_RATE_DURATION_MS && delta < 0) {
                String.format(Locale.US, ", %.1f%%/h", -delta * 3_600_000.0 / durationMillis)
            } else {
                ""
            }
            "battery=$first%->$last% (${if (delta > 0) "+" else ""}$delta$rate)"
        }
        val charging = when (chargingSamples) {
            0 -> "never"
            samples -> "always"
            else -> "${chargingSamples * 100 / samples}% of the time"
        }
        return "Session power: duration=${formatDuration(durationMillis)}, mode=$powerModeLabel, " +
            "$battery, charging=$charging, thermalMax=${thermalLabel(maxThermalStatus)}, " +
            "avgFpsCap=${String.format(Locale.US, "%.1f", frameRateSum.toDouble() / samples)}, " +
            "avgBitrate=${bitrateSum / samples / 1000}kbps, " +
            "saving=${savingSamples * 100 / samples}% of the time, samples=$samples."
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1_000L
        return "${totalSeconds / 60}m${(totalSeconds % 60).toString().padStart(2, '0')}s"
    }

    private companion object {
        const val MIN_RATE_DURATION_MS = 10L * 60L * 1_000L
    }
}
