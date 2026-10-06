package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import io.github.mnvkalyansambhana.offgridvault.ui.pin.NewPinScreen
import io.github.mnvkalyansambhana.offgridvault.ui.pin.PinEntryScreen
import io.github.mnvkalyansambhana.offgridvault.ui.pin.SavingScreen

@Composable
fun ChangePinScreen(vm: ChangePinViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    BackHandler { if (!vm.back() && vm.step != ChangePinViewModel.Step.Saving) onCancel() }
    when (vm.step) {
        ChangePinViewModel.Step.Current -> PinEntryScreen(
            headline = if (vm.message != null) "not quite." else "current pin.",
            label = "Change PIN",
            supporting = if (vm.busy) "checking…" else "enter the PIN you use now",
            pinLength = vm.pinLength,
            message = vm.message,
            onDigit = vm::digit,
            onDelete = vm::deleteDigit,
            enabled = !vm.busy,
        )
        ChangePinViewModel.Step.NewPin -> NewPinScreen(vm.newPin, label = "Change PIN") { vm.onNewPin(it, onDone) }
        ChangePinViewModel.Step.Saving -> SavingScreen("saving.", "Change PIN", "tuning encryption to this phone and saving your new PIN…")
    }
}
