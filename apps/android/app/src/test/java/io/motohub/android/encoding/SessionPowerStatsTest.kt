// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Jose Luis Alcazar and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.encoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPowerStatsTest {
    private val thermal: (Int) -> String = { if (it == 0) "none" else "level$it" }

    @Test
    fun `a session that was never sampled has nothing to say`() {
        assertNull(SessionPowerStats().summary("Auto", thermal))
    }

    @Test
    fun `a long discharging session reports its drain per hour`() {
        val stats = SessionPowerStats()
        // Twenty minutes, 80% down to 74%, sampled at both ends.
        stats.sample(1_000L, 80, charging = false, thermalStatus = 0, frameRate = 30, bitrate = 4_000_000, saving = false)
        stats.sample(1_201_000L, 74, charging = false, thermalStatus = 2, frameRate = 20, bitrate = 2_000_000, saving = true)
        val summary = stats.summary("Auto", thermal)!!
        assertTrue(summary, summary.contains("duration=20m00s"))
        assertTrue(summary, summary.contains("battery=80%->74% (-6, 18.0%/h)"))
        assertTrue(summary, summary.contains("charging=never"))
        assertTrue(summary, summary.contains("thermalMax=level2"))
        assertTrue(summary, summary.contains("avgFpsCap=25.0"))
        assertTrue(summary, summary.contains("avgBitrate=3000kbps"))
        assertTrue(summary, summary.contains("saving=50% of the time"))
    }

    /** One step of the gauge over a few minutes is not a rate worth printing. */
    @Test
    fun `a short session gives the change without a rate`() {
        val stats = SessionPowerStats()
        stats.sample(0L, 50, charging = false, thermalStatus = 0, frameRate = 30, bitrate = 4_000_000, saving = false)
        stats.sample(120_000L, 49, charging = false, thermalStatus = 0, frameRate = 30, bitrate = 4_000_000, saving = false)
        assertTrue(stats.summary("Smooth", thermal)!!.contains("battery=50%->49% (-1)"))
    }

    @Test
    fun `a session on the charger reports no drain rate`() {
        val stats = SessionPowerStats()
        stats.sample(0L, 50, charging = true, thermalStatus = 0, frameRate = 30, bitrate = 4_000_000, saving = false)
        stats.sample(1_800_000L, 61, charging = true, thermalStatus = 0, frameRate = 30, bitrate = 4_000_000, saving = false)
        val summary = stats.summary("Auto", thermal)!!
        assertTrue(summary, summary.contains("battery=50%->61% (+11)"))
        assertTrue(summary, summary.contains("charging=always"))
    }

    @Test
    fun `a phone that reports no level says so`() {
        val stats = SessionPowerStats()
        stats.sample(0L, null, charging = false, thermalStatus = 0, frameRate = 30, bitrate = 4_000_000, saving = false)
        assertTrue(stats.summary("Auto", thermal)!!.contains("battery=unknown"))
    }

    @Test
    fun `reset starts a new session`() {
        val stats = SessionPowerStats()
        stats.sample(0L, 50, charging = false, thermalStatus = 0, frameRate = 30, bitrate = 4_000_000, saving = false)
        stats.reset()
        assertNull(stats.summary("Auto", thermal))
        assertEquals(null, stats.summary("Auto", thermal))
    }
}
