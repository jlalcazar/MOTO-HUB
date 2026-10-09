// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Vincenzo Buonomano and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.encoding

/**
 * Decides which encoded access units reach the transport when the live frame cap sits below the
 * encoder's own rate. Only ever touched on the drain thread, apart from the two reset calls.
 *
 * A GOP stream is never paced here: dropping a P-frame corrupts the decode until the next keyframe,
 * so its pacing happens at the input surface instead (the frame-cap listener).
 *
 * An all-intra stream used to be exempt as well, by accident: every frame on it is a keyframe, and
 * keyframes were always let through so that a reconnecting dash would not wait for its picture. So
 * on every all-intra mirroring session - a CFMOTO 800MT's CFDL26 dash among them - Power mode
 * Saver, Balanced, the thermal cap and the link backoff all lowered the bitrate and none of them
 * lowered the frame rate: the radio kept sending 30 full IDRs a second whatever the rider picked.
 * Each of those frames decodes on its own, so dropping one costs nothing but the frame itself.
 * Only a keyframe a consumer explicitly asked for ([forceNextKeyFrame]) still skips the queue.
 */
internal class EncodedFramePacer {
    @Volatile private var forceNextKeyFrame = true
    @Volatile private var nextDeadlineNanos = 0L

    /** A fresh stream: its first keyframe goes out immediately. */
    fun reset() {
        forceNextKeyFrame = true
        nextDeadlineNanos = 0L
    }

    /** The cap changed: start pacing again from the next frame instead of a stale deadline. */
    fun restartPacing() {
        nextDeadlineNanos = 0L
    }

    /** A consumer asked for a sync frame; the next keyframe is forwarded whatever the cap says. */
    fun forceNextKeyFrame() {
        forceNextKeyFrame = true
    }

    fun shouldForward(
        isKeyFrame: Boolean,
        gopStream: Boolean,
        frameCap: Int,
        baseFrameRate: Int,
        nowNanos: Long
    ): Boolean {
        if (gopStream) return true
        val intervalNanos = 1_000_000_000L / frameCap.coerceAtLeast(1)
        if (isKeyFrame && forceNextKeyFrame) {
            forceNextKeyFrame = false
            nextDeadlineNanos = nowNanos + intervalNanos
            return true
        }
        if (frameCap >= baseFrameRate) return true
        // Frames reach the drain thread a millisecond or two either side of their slot; without
        // the slack a 30 fps source capped to 20 loses every other frame and runs at 15.
        if (nowNanos + PACING_SLACK_NANOS < nextDeadlineNanos) return false
        // Advance from the slot, not from now, so the cap is met on average; only a source that
        // fell a whole interval behind restarts the schedule.
        val nextSlot = nextDeadlineNanos + intervalNanos
        nextDeadlineNanos = if (nextSlot <= nowNanos) nowNanos + intervalNanos else nextSlot
        return true
    }

    private companion object {
        const val PACING_SLACK_NANOS = 2_000_000L
    }
}
