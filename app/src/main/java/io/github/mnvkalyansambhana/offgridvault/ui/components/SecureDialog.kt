package io.github.mnvkalyansambhana.offgridvault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * The only dialog the app may use (T2). Dialogs are separate windows, so FLAG_SECURE is set
 * explicitly rather than inherited from the activity.
 */
@Composable
fun SecureDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        content = content,
    )
}

/** The only popup the app may use (T2), for the same reason as [SecureDialog]. */
@Composable
fun SecurePopup(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, securePolicy = SecureFlagPolicy.SecureOn),
        content = content,
    )
}
