package io.github.mnvkalyansambhana.offgridvault.ui.erase

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.DeviceCredential
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultEraser
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.InverseCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopVariant
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpInput
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S30 "erase vault and start over": typed ERASE + the phone's screen lock. */
class EraseViewModel(private val app: AppContainer) : ViewModel() {
    var typed by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val confirmed: Boolean get() = VaultEraser.isConfirmed(typed)

    fun credentialRefused() {
        message = "Screen lock not confirmed — nothing was erased."
    }

    fun noScreenLock(): Boolean = !app.isDeviceSecure()

    fun erase(onErased: () -> Unit) {
        if (!confirmed || busy) return
        busy = true
        viewModelScope.launch {
            withContext(Dispatchers.Default) { app.eraser.eraseEverything() }
            busy = false
            onErased()
        }
    }
}

@Composable
fun EraseScreen(vm: EraseViewModel, onErased: () -> Unit, onCancel: () -> Unit) {
    val activity = LocalActivity.current
    TwoToneScreen(
        hero = {
            HeroBand(headline = "erase vault.", label = "Start over", labelColor = OffGridColors.Coral)
        },
    ) {
        InverseCard(Modifier.fillMaxWidth()) {
            Label("Permanent", color = OffGridColors.Coral, style = OffGridType.LabelSmall)
            Body(
                "This deletes every password in this vault, for good. There's no undo and no backup — " +
                    "not even your recovery words can bring it back.",
                color = OffGridColors.TextOnDarkBody,
            )
        }
        Body("Afterwards you'll set up a new, empty vault with a new PIN.", color = OffGridColors.TextOnLight2)
        SharpInput(
            value = vm.typed,
            onValueChange = { vm.typed = it },
            label = "Type ${VaultEraser.CONFIRMATION_WORD} to confirm",
            capitalization = KeyboardCapitalization.Characters,
            modifier = Modifier.fillMaxWidth(),
        )
        vm.message?.let { Body(it, color = OffGridColors.CoralDeep) }
        PopButton(
            text = if (vm.busy) "Erasing…" else "Erase everything",
            variant = PopVariant.Destructive,
            enabled = vm.confirmed && !vm.busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (vm.noScreenLock() || activity == null) {
                    vm.credentialRefused()
                } else {
                    DeviceCredential.confirm(
                        activity,
                        title = "Erase OffGrid Vault",
                        description = "Confirm your phone's screen lock to erase the vault",
                    ) { ok -> if (ok) vm.erase(onErased) else vm.credentialRefused() }
                }
            },
        )
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, enabled = !vm.busy, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Label("Cancel", color = OffGridColors.TextOnLight2) }
    }
}
