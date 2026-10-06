package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.IconButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.OffGridIcon
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopVariant
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpInput
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/** "Pop · Vault list" (design/screens/pop/PopVaultList.dc.html). */
@Composable
fun VaultHomeScreen(
    session: VaultSession,
    hasRecovery: (VaultHeader) -> Boolean,
    onOpenEntry: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
) {
    val state by session.state.collectAsState()
    val unlocked = state as? VaultSession.State.Unlocked ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var noticeDismissed by rememberSaveable { mutableStateOf(false) }
    val entries = unlocked.content.search(query)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeroBand(
                headline = "vault.",
                label = "${unlocked.content.entries.size} logins",
                navigation = {
                    Spacer(Modifier.weight(1f))
                    IconButton(OffGridIcon.Lock, "Lock now", onLock)
                    // P20: the only reminder for missing recovery words on this screen.
                    IconButton(OffGridIcon.Gear, "Settings", onSettings, badge = !hasRecovery(unlocked.header))
                },
            ) {
                val failures = unlocked.failedAttemptsBefore
                if (failures > 0 && !noticeDismissed) {
                    AmberStrip("$failures wrong PIN ${if (failures == 1) "attempt" else "attempts"} since your last unlock") {
                        noticeDismissed = true
                    }
                }
                if (unlocked.restoredFromPrevious) {
                    AmberStrip("Restored from previous save — your last change may be missing", onDismiss = null)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
                item { SharpInput(value = query, onValueChange = { query = it }, label = "Search", modifier = Modifier.fillMaxWidth()) }
                if (entries.isEmpty()) {
                    item {
                        Body(
                            if (query.isBlank()) "no logins yet. tap ADD to save your first one." else "nothing matches \"$query\".",
                            color = OffGridColors.TextOnLight2,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(entries, key = { it.id }) { entry -> EntryRow(entry) { onOpenEntry(entry.id) } }
                item {
                    Label("Locks after 5 min idle · on screen-off", style = OffGridType.LabelSmall, modifier = Modifier.padding(top = 20.dp, bottom = 96.dp))
                }
            }
        }
        PopButton(
            text = "+ Add",
            onClick = onAdd,
            variant = PopVariant.Mint,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 24.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun EntryRow(entry: EntryView, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).border(1.5.dp, OffGridColors.Ink), contentAlignment = Alignment.Center) {
            BasicText(entry.title.take(1).lowercase().ifEmpty { "·" }, style = OffGridType.Headline.copy(fontSize = 22.sp, color = OffGridColors.Ink))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Body(entry.title.ifEmpty { "(untitled)" }, style = OffGridType.BodyStrong)
            if (entry.username.isNotEmpty()) Body(entry.username, color = OffGridColors.TextOnLight2)
        }
        Label("›", color = OffGridColors.InputBorder)
    }
}

@Composable
private fun AmberStrip(text: String, onDismiss: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().background(OffGridColors.Amber).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(text, color = OffGridColors.Ink, style = OffGridType.LabelSmall, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
        if (onDismiss != null) IconButton(OffGridIcon.Close, "Dismiss", onDismiss, tint = OffGridColors.Ink)
    }
}
