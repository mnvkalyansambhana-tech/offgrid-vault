package io.github.mnvkalyansambhana.offgridvault.ui.unlock

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinDots
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinPad
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/** "Pop · Unlock" / "Pop · Wrong PIN" (design/screens/pop). */
@Composable
fun UnlockScreen(vm: UnlockViewModel, onUnlocked: () -> Unit, onNoVault: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        vm.refresh() // S22: screen lock may have been removed in Settings
        onPauseOrDispose {}
    }
    val problem = vm.problem
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(
                headline = when (problem) {
                    UnlockViewModel.Problem.WrongPin -> "not quite."
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
            ) {
                PinDots(vm.pinLength, error = problem == UnlockViewModel.Problem.WrongPin)
                problemMessage(problem)?.let { message ->
                    Box(Modifier.fillMaxWidth().border(1.5.dp, OffGridColors.Coral).padding(12.dp)) {
                        Body(message, color = OffGridColors.TextOnDarkBody)
                    }
                }
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        PinPad(
            onDigit = { vm.digit(it, onUnlocked, onNoVault) },
            onDelete = vm::deleteDigit,
            enabled = !vm.busy && problem != UnlockViewModel.Problem.NoScreenLock,
        )
        Label("Recovery words & lockout arrive in M4", style = OffGridType.LabelSmall, color = OffGridColors.TextOnLight2)
    }
}

private fun problemMessage(problem: UnlockViewModel.Problem?): String? = when (problem) {
    UnlockViewModel.Problem.WrongPin -> "Wrong PIN. Try again."
    UnlockViewModel.Problem.NoScreenLock ->
        "Your phone has no screen lock. Set a PIN, pattern or password in Settings, then come back."
    UnlockViewModel.Problem.DeviceKeyLost ->
        "This phone's secure key for the vault is gone, so the vault can't be opened here."
    UnlockViewModel.Problem.Unreadable ->
        "The vault file is damaged or was made by a newer version of the app."
    null -> null
}
