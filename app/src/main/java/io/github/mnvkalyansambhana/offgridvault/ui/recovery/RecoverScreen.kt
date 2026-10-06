package io.github.mnvkalyansambhana.offgridvault.ui.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpInput
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.pin.NewPinScreen
import io.github.mnvkalyansambhana.offgridvault.ui.pin.SavingScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/** "Pop · Enter recovery words" → new PIN. */
@Composable
fun RecoverScreen(vm: RecoverViewModel, onDone: () -> Unit, onCancel: () -> Unit, onErase: () -> Unit) {
    BackHandler { if (!vm.back() && vm.step != RecoverViewModel.Step.Saving) onCancel() }
    // The vault is already open (in memory) while a new PIN is chosen: leaving the app abandons
    // the recovery, so a phone left on this screen can't be picked up and given a new PIN.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (vm.step == RecoverViewModel.Step.NewPin) onCancel()
    }
    when (vm.step) {
        RecoverViewModel.Step.Words -> WordsEntry(vm, onErase)
        RecoverViewModel.Step.NewPin -> NewPinScreen(vm.newPin, label = "Recovery") { vm.onNewPin(it, onDone) }
        RecoverViewModel.Step.Saving -> SavingScreen("saving.", "Recovery", "tuning encryption to this phone and saving your new PIN…")
    }
}

@Composable
private fun WordsEntry(vm: RecoverViewModel, onErase: () -> Unit) {
    TwoToneScreen(
        hero = {
            HeroBand(
                headline = "12 words, in order.",
                label = "Recovery",
                supporting = if (vm.busy) "checking…" else "you can type them all into the first box, separated by spaces",
            )
        },
    ) {
        (0 until 12 step 2).forEach { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(i, i + 1).forEach { index ->
                    SharpInput(
                        value = vm.words[index],
                        onValueChange = { vm.setWord(index, it) },
                        label = "%02d".format(index + 1),
                        isError = !vm.isWordOk(index),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val firstBad = vm.words.indices.firstOrNull { !vm.isWordOk(it) }
        if (firstBad != null) {
            Label("Word %02d isn't on the word list — check the spelling".format(firstBad + 1), color = OffGridColors.CoralDeep, style = OffGridType.LabelSmall)
        }
        vm.error?.let { Body(it, color = OffGridColors.CoralDeep) }
        Body("checked as you type · keyboard suggestions off", color = OffGridColors.TextOnLight2)
        PopButton(
            "Unlock & set new PIN",
            onClick = vm::submitWords,
            enabled = vm.allWordsValid && !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onErase),
            contentAlignment = Alignment.Center,
        ) { Label("Lost your words? Erase & start over", color = OffGridColors.TextOnLight2, style = OffGridType.LabelSmall) }
    }
}
