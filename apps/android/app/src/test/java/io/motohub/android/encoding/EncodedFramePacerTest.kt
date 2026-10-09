// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Vincenzo Buonomano and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.encoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodedFramePacerTest {
    private val frameNanos = 1_000_000_000L / 30

    /** Feeds one second of a 30 fps source and counts what reaches the transport. */
    private fun forwardedInOneSecond(
        pacer: EncodedFramePacer,
        frameCap: Int,
        gopStream: Boolean = false,
        isKeyFrame: Boolean = true,
        startNanos: Long = 1_000_000_000L
    ): Int = (0 until 30).count { frame ->
        pacer.shouldForward(
            isKeyFrame = isKeyFrame,
            gopStream = gopStream,
            frameCap = frameCap,
            baseFrameRate = 30,
            nowNanos = startNanos + frame * frameNanos
        )
    }

    @Test
    fun `an all-intra stream at its own rate forwards every frame`() {
        assertEquals(30, forwardedInOneSecond(EncodedFramePacer(), frameCap = 30))
    }

    @Test
    fun `an all-intra stream honours the Saver frame cap`() {
        // Every frame is a keyframe here, and keyframes used to bypass the cap entirely: Power
        // mode Saver left a CFMOTO 800MT mirroring session at 30 IDRs a second.
        assertEquals(20, forwardedInOneSecond(EncodedFramePacer(), frameCap = 20))
    }

    @Test
    fun `an all-intra stream honours the link backoff floor`() {
        assertEquals(12, forwardedInOneSecond(EncodedFramePacer(), frameCap = 12))
    }

    @Test
    fun `a GOP stream is never paced on the output side`() {
        assertEquals(
            30,
            forwardedInOneSecond(EncodedFramePacer(), frameCap = 12, gopStream = true, isKeyFrame = false)
        )
    }

    @Test
    fun `a requested keyframe skips the cap`() {
        val pacer = EncodedFramePacer()
        val start = 1_000_000_000L
        assertTrue(pacer.shouldForward(true, false, 12, 30, start))
        // The very next frame would normally be paced out at 12 fps.
        pacer.forceNextKeyFrame()
        assertTrue(pacer.shouldForward(true, false, 12, 30, start + frameNanos))
    }

    @Test
    fun `the first keyframe of a stream is always forwarded`() {
        val pacer = EncodedFramePacer()
        pacer.reset()
        assertTrue(pacer.shouldForward(true, false, 1, 30, 5_000_000L))
    }

    @Test
    fun `a stalled source restarts the schedule instead of bursting`() {
        val pacer = EncodedFramePacer()
        forwardedInOneSecond(pacer, frameCap = 20)
        // Ten seconds of nothing, then the source resumes: the cap holds from the first frame on.
        assertEquals(
            20,
            forwardedInOneSecond(pacer, frameCap = 20, startNanos = 12_000_000_000L)
        )
    }
}
