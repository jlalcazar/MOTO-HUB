// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Jose Luis Alcazar and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.session

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import io.motohub.android.feature.settings.MotoHubSettings

/**
 * Applies the "Keep the screen on" setting to [activity]'s window.
 *
 * A window flag rather than a wake lock: it holds the screen only while this app is the one in
 * front, and Android drops it by itself the moment the rider leaves - nothing to release, and
 * nothing that can keep the phone awake in a pocket. The Android Auto preview and the display
 * dimmer manage their own windows and are not affected by it.
 */
internal fun applyKeepScreenOn(
    activity: Activity,
    enabled: Boolean = MotoHubSettings.keepScreenOn(activity)
) {
    if (enabled) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/** The activity behind a Compose [Context], which may be wrapped (a themed or localized context). */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
