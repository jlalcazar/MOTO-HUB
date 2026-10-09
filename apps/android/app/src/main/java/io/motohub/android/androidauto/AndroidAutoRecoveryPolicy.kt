// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Vincenzo Buonomano and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.androidauto

/** Pure recovery-timing logic for a full Android Auto session — no AGPL dependency, kept in
 *  shared code (and tested from both flavors) even though only Core's AndroidAutoSessionService
 *  currently calls it. */
internal fun shouldAutoRecoverAndroidAuto(
    hasReachedStreaming: Boolean,
    enabled: Boolean
): Boolean = hasReachedStreaming && enabled

/**
 * Why Android Auto is not going to be reconnected, or null when it is.
 *
 * Split out of the service so the sentence a rider log will carry is decided somewhere a test can
 * read it. It exists at all because the silent version cost a full reading of rider 8d5a1631's log
 * (2026-08-26): the session ended, the log stopped, and nothing said whether reconnection had been
 * refused, attempted, or had failed without a trace.
 */
internal fun androidAutoRecoveryRefusal(hasReachedStreaming: Boolean, enabled: Boolean): String? =
    when {
        shouldAutoRecoverAndroidAuto(hasReachedStreaming, enabled) -> null
        // A session that never streamed is named first even when the switch is also off: there is
        // nothing to reconnect TO, and blaming the switch would send a rider to change a setting
        // that would not have helped.
        !hasReachedStreaming -> "this session never reached streaming."
        else -> "automatic reconnection is switched off in this app."
    }

/**
 * What Core should do with the auto-recovery field a companion app sent: the value to store, or
 * null to leave Core's own switch alone.
 *
 * The gate is the entire content of this decision. `false` reaches Core both from a companion that
 * means "do not reconnect" and from one that predates the field and means nothing at all, and only
 * [provided] separates them. Read without it, every old companion becomes a switch that quietly
 * turns reconnection off for a rider who turned it on in Core - which is the fault this field was
 * added to fix, reintroduced from the other side.
 */
internal fun companionAutoRecovery(provided: Boolean, value: Boolean): Boolean? =
    if (provided) value else null

internal fun isAndroidAutoWatchdogStalled(
    nowElapsed: Long,
    lastProgressElapsed: Long,
    thresholdMillis: Long
): Boolean = lastProgressElapsed > 0L && nowElapsed - lastProgressElapsed >= thresholdMillis

/** Fewer accepted frames than this in one watchdog tick is under one frame a second. */
internal const val ANDROID_AUTO_STARVED_FRAMES_PER_TICK = 5L

/** How many starved ticks in a row make a stream worth rebuilding. */
internal const val ANDROID_AUTO_STARVED_TICKS = 3

/**
 * Counts consecutive watchdog ticks in which the stream was starved: the transport was refusing
 * or dropping frames, and barely any got through.
 *
 * The stall check above only sees a stream that has stopped completely. A T-Box that accepts one
 * frame and then blocks the next for the five seconds `pushFrame()` is allowed looks alive to it
 * forever - a frame did arrive inside every window - while the TFT shows a slideshow. That is the
 * congestion RIDEDAEMON_EOF_FIX.md describes, with the counters it added and nothing reading them.
 *
 * Both conditions are needed. Few frames alone is a quiet screen: Android Auto sends little when
 * the map is not moving, and nothing is lost. Lost frames alone is a busy link the adaptive
 * controller is already backing off for, and one that still carries a picture.
 *
 * @return the new streak; it is back to zero the first tick the stream is not starved.
 */
internal fun nextAndroidAutoStarvedTicks(
    previousTicks: Int,
    acceptedThisTick: Long,
    lostThisTick: Long
): Int = if (lostThisTick > 0L && acceptedThisTick < ANDROID_AUTO_STARVED_FRAMES_PER_TICK) {
    previousTicks + 1
} else {
    0
}

internal fun isAndroidAutoStreamStarved(starvedTicks: Int): Boolean =
    starvedTicks >= ANDROID_AUTO_STARVED_TICKS
