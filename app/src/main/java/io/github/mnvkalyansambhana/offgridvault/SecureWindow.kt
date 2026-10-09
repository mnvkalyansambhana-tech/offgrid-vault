package io.github.mnvkalyansambhana.offgridvault

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager

/**
 * Window-level protections every OffGrid Vault activity must apply in `onCreate`,
 * before any content is set.
 */
object SecureWindow {

    fun apply(activity: Activity) {
        val window = activity.window
        // Hard constraint: no screenshots, screen recording, casting or recents thumbnails.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val root = window.decorView
        // S20: other autofill services must never capture or offer to save vault contents.
        root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        // Tapjacking: ignore touches while another app's window is drawn over ours.
        root.filterTouchesWhenObscured = true
        // S25 (API 34+): only real accessibility tools (TalkBack, Switch Access…) may read our
        // views; other accessibility services see nothing. Set on the root, it covers every
        // screen — in a password manager almost everything on screen is sensitive.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            root.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }
    }
}
