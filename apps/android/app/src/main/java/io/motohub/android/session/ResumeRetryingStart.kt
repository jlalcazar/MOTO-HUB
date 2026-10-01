// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Vincenzo Buonomano and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.session

/**
 * A start Android may refuse because the activity is not in front yet, retried once on resume.
 *
 * The screen-capture consent result is delivered while the activity is started but not yet
 * resumed, and some ROMs refuse a foreground-service start in that window (WB-27: Xiaomi,
 * Android 16). The autostart on connect meets the same refusal from the other side: it fires
 * when the T-Box link comes up, and with MOTO-HUB in the background Android 12+ refuses the
 * Android Auto service outright (WB-28). [request] tries at once and, on
 * refusal, holds the value; [onResume] tries the held value exactly once more. The value is held
 * in memory only: a consent token does not survive a process restart, so there is nothing worth
 * saving across one.
 */
class ResumeRetryingStart<T : Any>(private val start: (T) -> Boolean) {
    private var pending: T? = null

    /** True when [start] succeeded now; false when it was refused and is held for the resume. */
    fun request(value: T): Boolean {
        pending = null
        if (start(value)) return true
        pending = value
        return false
    }

    /** Null when nothing was held; otherwise whether the single retry succeeded. */
    fun onResume(): Boolean? {
        val held = pending ?: return null
        pending = null
        return start(held)
    }

    /** Drops a held value without trying it; true when there was one to drop. */
    fun discard(): Boolean {
        val held = pending != null
        pending = null
        return held
    }
}
