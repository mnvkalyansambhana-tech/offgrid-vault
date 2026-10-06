package io.github.mnvkalyansambhana.offgridvault.ui.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinDots
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinPad
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors

@Composable
fun RecoveryLaterScreen(vm: RecoveryLaterViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    BackHandler { if (!vm.back() && vm.step != RecoveryLaterViewModel.Step.Saving) onCancel() }
    when (vm.step) {
        RecoveryLaterViewModel.Step.EnterPin -> TwoToneScreen(
            scrollable = false,
            hero = {
                HeroBand(
                    headline = if (vm.wrongPin) "not quite." else "confirm your pin.",
                    label = "Recovery words",
                    supporting = when {
                        vm.busy -> "checking…"
                        vm.wrongPin -> "Wrong PIN · ${vm.attemptsLeft} left before the vault locks"
                        else -> "only you can create recovery words for this vault"
                    },
                ) {
                    PinDots(vm.pinLength, error = vm.wrongPin)
                }
            },
        ) {
            Spacer(Modifier.weight(1f))
            PinPad(onDigit = vm::digit, onDelete = vm::deleteDigit, enabled = !vm.busy)
        }
        RecoveryLaterViewModel.Step.Words -> RecoveryWordsFlow(
            state = vm.recovery,
            onUnderstood = { vm.save(onDone) },
            onSkip = null,
            stepLabel = { "Recovery words" },
        )
        RecoveryLaterViewModel.Step.Saving -> TwoToneScreen(
            scrollable = false,
            hero = { HeroBand(headline = "saving.", label = "Recovery words") },
        ) {
            Body("binding your words to this vault…", color = OffGridColors.TextOnLight2)
        }
    }
}
