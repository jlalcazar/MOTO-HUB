// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Vincenzo Buonomano and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.session

/**
 * A start Android may refuse because the activity is not in front yet, retried once on resume.
 *
 * The screen-capture consent result is delivered while the activity is started but not yet
 * resumed, and some ROMs refuse a foreground-service start in that window (WB-27: Xiaomi,
 * Android 16). [request] tries at once and, on refusal, holds the value; [onResume] tries the
 * held value exactly once more. The value is held in memory only: a consent token does not
 * survive a process restart, so there is nothing worth saving across one.
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
}
