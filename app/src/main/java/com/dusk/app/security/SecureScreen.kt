package com.dusk.app.security

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.SecureFlagPolicy
import com.dusk.app.BuildConfig

object SecureScreens {
    /** Debug builds only: lets the emulator test take screenshots of journal screens. Ignored in real builds. */
    @Volatile var allowCaptureInTests = false

    private var active = 0

    /** For sheets and dialogs, which have their own window: secure when they show journal text. */
    fun policy(secure: Boolean): SecureFlagPolicy =
        if (secure && !(BuildConfig.DEBUG && allowCaptureInTests)) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit

    internal fun enter(activity: Activity) {
        active++
        if (!(BuildConfig.DEBUG && allowCaptureInTests)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    internal fun leave(activity: Activity) {
        active = (active - 1).coerceAtLeast(0)
        if (active == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/** While this is on screen: no screenshots, no screen recording, and a blank card in recent apps. */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        SecureScreens.enter(activity)
        onDispose { SecureScreens.leave(activity) }
    }
}
