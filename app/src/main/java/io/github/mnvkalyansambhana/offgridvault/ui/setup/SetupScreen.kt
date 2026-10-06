package io.github.mnvkalyansambhana.offgridvault.ui.setup

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinDots
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinPad
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopVariant
import io.github.mnvkalyansambhana.offgridvault.ui.components.SecureDialog
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoveryWordsFlow
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoveryWordsState
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import io.github.mnvkalyansambhana.offgridvault.ui.welcome.WelcomeScreen

@Composable
fun SetupScreen(vm: SetupViewModel, onCreated: () -> Unit) {
    BackHandler(enabled = vm.step != SetupViewModel.Step.Welcome && vm.step != SetupViewModel.Step.Sealing) {
        vm.back()
    }
    LifecycleResumeEffect(Unit) {
        vm.refreshDeviceSecure() // user may return from Settings with a screen lock set (S22)
        onPauseOrDispose {}
    }
    val context = LocalContext.current
    when (vm.step) {
        SetupViewModel.Step.Welcome -> WelcomeScreen(
            deviceSecure = vm.deviceSecure,
            onSetUp = vm::start,
            onOpenSecuritySettings = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
        )
        SetupViewModel.Step.CreatePin, SetupViewModel.Step.ConfirmPin -> PinStep(vm)
        SetupViewModel.Step.Recovery -> RecoveryWordsFlow(
            state = vm.recovery,
            onUnderstood = { vm.finish(onCreated) },
            onSkip = vm::requestSkip,
            stepLabel = { step ->
                when (step) {
                    RecoveryWordsState.Step.Intro, RecoveryWordsState.Step.Words -> "Step 2 of 4"
                    RecoveryWordsState.Step.Check -> "Step 3 of 4"
                    RecoveryWordsState.Step.Limits -> "Step 4 of 4"
                }
            },
        )
        SetupViewModel.Step.Sealing -> Sealing(vm.sealingFailed) { vm.finish(onCreated) }
    }
    if (vm.askSkipConfirmation) SkipRecoveryDialog(onCancel = vm::cancelSkip, onSkip = { vm.confirmSkip(onCreated) })
}

@Composable
private fun PinStep(vm: SetupViewModel) {
    val confirming = vm.step == SetupViewModel.Step.ConfirmPin
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(
                headline = if (confirming) "confirm it." else "pick a pin.",
                label = "Step 1 of 4",
                supporting = if (confirming) "type the same 6 digits again" else "6 digits · different from your screen lock",
            ) {
                PinDots(vm.pinLength, error = vm.pinMessage != null)
                vm.pinMessage?.let { message ->
                    Box(Modifier.fillMaxWidth().border(1.5.dp, OffGridColors.Coral).padding(12.dp)) {
                        Label(message, color = OffGridColors.Coral, style = OffGridType.LabelSmall)
                    }
                }
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        PinPad(onDigit = vm::digit, onDelete = vm::deleteDigit)
        Label(
            "In-app keypad — your keyboard never sees it",
            style = OffGridType.LabelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** P19: skipping is allowed, but only after an explicit warning. */
@Composable
private fun SkipRecoveryDialog(onCancel: () -> Unit, onSkip: () -> Unit) {
    SecureDialog(onDismissRequest = onCancel) {
        Column {
            HeroBand(headline = "skip for now?", label = "Recovery words", labelColor = OffGridColors.Coral, headlineStyle = OffGridType.HeadlineSheet)
            Column(
                Modifier.background(OffGridColors.Paper).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Body("Without recovery words, forgetting your PIN locks this vault forever. You can set them up later from the vault.")
                PopButton("Set them up now", onClick = onCancel, modifier = Modifier.fillMaxWidth())
                PopButton("Skip for now", onClick = onSkip, variant = PopVariant.Destructive, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Sealing(failed: Boolean, onRetry: () -> Unit) {
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(
                headline = if (failed) "couldn't seal it." else "sealing your vault.",
                label = if (failed) "Setup error" else "Almost done",
                labelColor = if (failed) OffGridColors.Coral else OffGridColors.TextOnDarkLabel,
            )
        },
    ) {
        if (failed) {
            Body("This phone's secure key store refused to create the vault key. Try again; if it keeps failing, restart the phone.")
            Spacer(Modifier.weight(1f))
            PopButton("Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
        } else {
            Body("tuning encryption to this phone and locking your vault… about a second.", color = OffGridColors.TextOnLight2)
        }
    }
}
