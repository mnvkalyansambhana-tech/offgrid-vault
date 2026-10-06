package io.github.mnvkalyansambhana.offgridvault.ui.unlock

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.mnvkalyansambhana.offgridvault.ui.pin.PinEntryScreen

/** "Pop · Unlock" / "Pop · Wrong PIN" (design/screens/pop). */
@Composable
fun UnlockScreen(vm: UnlockViewModel, nav: UnlockViewModel.Navigation, onForgotPin: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        vm.refresh() // S22: screen lock may have been removed in Settings
        onPauseOrDispose {}
    }
    val problem = vm.problem
    PinEntryScreen(
        headline = when (problem) {
            is UnlockViewModel.Problem.WrongPin -> "not quite."
            UnlockViewModel.Problem.NoScreenLock -> "screen lock needed."
            UnlockViewModel.Problem.DeviceKeyLost, UnlockViewModel.Problem.Unreadable -> "can't open vault."
            null -> "welcome back."
        },
        label = "OffGrid Vault",
        supporting = when {
            vm.busy -> "unlocking…"
            problem == null -> "enter your 6-digit PIN"
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
            null -> null
        },
        onDigit = { vm.digit(it, nav) },
        onDelete = vm::deleteDigit,
        enabled = !vm.busy && problem != UnlockViewModel.Problem.NoScreenLock,
        linkText = "Forgot PIN?",
        onLink = onForgotPin,
    )
}
