package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** M3/M4 placeholder for "Pop · Vault list": proves the vault opened. Entries arrive in M5. */
@Composable
fun VaultHomeScreen(
    session: VaultSession,
    hasRecovery: (VaultHeader) -> Boolean,
    onSetUpRecovery: () -> Unit,
    onChangePin: () -> Unit,
    onLock: () -> Unit,
) {
    var noticeDismissed by rememberSaveable { mutableStateOf(false) }
    val state by session.state.collectAsState()
    val unlocked = state as? VaultSession.State.Unlocked
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(
                headline = "vault.",
                label = "${unlocked?.vault?.entries?.size ?: 0} logins",
            ) {
                val failures = unlocked?.failedAttemptsBefore ?: 0
                if (failures > 0 && !noticeDismissed) {
                    // S12: tells the owner someone else may have been trying.
                    Row(
                        Modifier.fillMaxWidth().background(OffGridColors.Amber).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label(
                            "$failures wrong PIN ${if (failures == 1) "attempt" else "attempts"} since your last unlock",
                            color = OffGridColors.Ink,
                            style = OffGridType.LabelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier.size(44.dp).clickable(role = Role.Button) { noticeDismissed = true }
                                .semantics { contentDescription = "Dismiss" },
                            contentAlignment = Alignment.Center,
                        ) { Label("✕", color = OffGridColors.Ink) }
                    }
                }
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
        // Temporary home for Change PIN until the Settings screen (M5).
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onChangePin),
            contentAlignment = Alignment.Center,
        ) { Label("Change PIN", color = OffGridColors.Green) }
        PopButton("Lock now", onClick = {
            session.lock()
            onLock()
        }, modifier = Modifier.fillMaxWidth())
    }
}
