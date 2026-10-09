package io.github.mnvkalyansambhana.offgridvault.ui.unlock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.mnvkalyansambhana.offgridvault.Biometrics
import io.github.mnvkalyansambhana.offgridvault.ui.components.Icon
import io.github.mnvkalyansambhana.offgridvault.ui.components.OffGridIcon
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopVariant
import io.github.mnvkalyansambhana.offgridvault.ui.pin.PinEntryScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors

/** "Pop · Unlock" / "Pop · Wrong PIN" (design/screens/pop), with the fingerprint key (M6). */
@Composable
fun UnlockScreen(vm: UnlockViewModel, nav: UnlockViewModel.Navigation, onForgotPin: () -> Unit) {
    val activity = LocalActivity.current
    // Prompt for the fingerprint once on arrival; afterwards only via the key.
    var autoPrompted by rememberSaveable { mutableStateOf(false) }

    fun promptFingerprint() {
        val host = activity ?: return
        val operation = vm.fingerprintOperation() ?: return
        Biometrics.authenticate(host, "Unlock OffGrid Vault", "Use your fingerprint", operation.cipher) { cipher ->
            if (cipher != null) vm.onFingerprintSuccess(operation, nav)
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.refresh() // S22: screen lock may have been removed in Settings
        if (!autoPrompted && vm.fingerprintReady) {
            autoPrompted = true
            promptFingerprint()
        }
        onPauseOrDispose {}
    }
    val problem = vm.problem
    PinEntryScreen(
        headline = when (problem) {
            is UnlockViewModel.Problem.WrongPin -> "not quite."
            UnlockViewModel.Problem.NoScreenLock -> "screen lock needed."
            UnlockViewModel.Problem.DeviceKeyLost, UnlockViewModel.Problem.Unreadable -> "can't open vault."
            UnlockViewModel.Problem.FingerprintChanged, UnlockViewModel.Problem.FingerprintFailed, null -> "welcome back."
        },
        label = "OffGrid Vault",
        supporting = when {
            vm.busy -> "unlocking…"
            problem == null || problem is UnlockViewModel.Problem.FingerprintChanged ||
                problem is UnlockViewModel.Problem.FingerprintFailed -> "enter your 6-digit PIN"
            else -> null
        },
        pinLength = vm.pinLength,
        message = when (problem) {
            is UnlockViewModel.Problem.WrongPin -> {
                val left = problem.attemptsLeft
                "Wrong PIN · $left ${if (left == 1) "attempt" else "attempts"} left. " +
                    if (left == 1) "After that, only your recovery words can unlock." else ""
            }
            UnlockViewModel.Problem.NoScreenLock ->
                "Your phone has no screen lock. Set a PIN, pattern or password in Settings, then come back."
            UnlockViewModel.Problem.DeviceKeyLost ->
                "This phone's secure key for the vault is gone, so the vault can't be opened here."
            UnlockViewModel.Problem.Unreadable ->
                "The vault file is damaged or was made by a newer version of the app."
            UnlockViewModel.Problem.FingerprintChanged ->
                "A fingerprint was added to this phone, so fingerprint unlock was turned off. " +
                    "Unlock with your PIN, then turn it back on in Settings."
            UnlockViewModel.Problem.FingerprintFailed ->
                "Fingerprint unlock stopped working and was turned off. Unlock with your PIN."
            null -> null
        },
        warning = problem is UnlockViewModel.Problem.FingerprintChanged ||
            problem is UnlockViewModel.Problem.FingerprintFailed,
        onDigit = { vm.digit(it, nav) },
        onDelete = vm::deleteDigit,
        enabled = !vm.busy && problem != UnlockViewModel.Problem.NoScreenLock,
        linkText = "Forgot PIN?",
        onLink = onForgotPin,
        bottomLeft = if (!vm.fingerprintReady) null else {
            {
                PopButton(
                    text = "",
                    onClick = ::promptFingerprint,
                    variant = PopVariant.Mint,
                    enabled = !vm.busy,
                    modifier = Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "Unlock with fingerprint" },
                ) { Icon(OffGridIcon.Fingerprint, OffGridColors.Ink) }
            }
        },
    )
}
