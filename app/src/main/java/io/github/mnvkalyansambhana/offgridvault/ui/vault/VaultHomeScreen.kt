package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/** M3 placeholder for "Pop · Vault list": proves the vault opened. Entries arrive in M5. */
@Composable
fun VaultHomeScreen(
    session: VaultSession,
    hasRecovery: (VaultHeader) -> Boolean,
    onSetUpRecovery: () -> Unit,
    onLock: () -> Unit,
) {
    val state by session.state.collectAsState()
    val unlocked = state as? VaultSession.State.Unlocked
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(
                headline = "vault.",
                label = "${unlocked?.vault?.entries?.size ?: 0} logins",
            ) {
                if (unlocked?.restoredFromPrevious == true) {
                    Box(Modifier.fillMaxWidth().background(OffGridColors.Amber).padding(12.dp)) {
                        Label("Restored from previous save — your last change may be missing", color = OffGridColors.Ink, style = OffGridType.LabelSmall)
                    }
                }
            }
        },
    ) {
        if (unlocked != null && !hasRecovery(unlocked.header)) {
            // P19: persistent until recovery words are set up; no periodic reminders.
            Column(
                Modifier.fillMaxWidth().background(OffGridColors.Amber).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Label("No recovery words yet", color = OffGridColors.Ink, style = OffGridType.LabelSmall)
                Body("Forget your PIN and this vault is lost.", color = OffGridColors.Ink)
                PopButton("Set up recovery words", onClick = onSetUpRecovery, modifier = Modifier.fillMaxWidth())
            }
        }
        SharpCard(Modifier.fillMaxWidth()) {
            Label("Unlocked", color = OffGridColors.Green, style = OffGridType.LabelSmall)
            Body("Your vault is open. Adding and viewing logins arrives in M5.")
        }
        Spacer(Modifier.weight(1f))
        PopButton("Lock now", onClick = {
            session.lock()
            onLock()
        }, modifier = Modifier.fillMaxWidth())
    }
}
