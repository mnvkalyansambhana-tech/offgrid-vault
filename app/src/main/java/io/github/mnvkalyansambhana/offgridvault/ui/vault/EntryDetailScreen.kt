package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth as fillWidthFraction
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultContent
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.IconButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.InverseCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.OffGridIcon
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopVariant
import io.github.mnvkalyansambhana.offgridvault.ui.components.SecureDialog
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import java.text.DateFormat
import java.util.Date

private const val MASK = "••••••••••"

/** "Pop · Entry detail" + "Pop · Delete" (design/screens/pop). */
@Composable
fun EntryDetailScreen(vm: EntryDetailViewModel, session: VaultSession, onBack: () -> Unit, onEdit: () -> Unit) {
    session.state.collectAsState().value // recompose on saves
    val entry = vm.entry() ?: return
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // S8/S10: leaving the app re-masks immediately.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.maskAll() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeroBand(
                headline = entry.title.lowercase().ifEmpty { "untitled" } + ".",
                label = "Login",
                navigation = {
                    IconButton(OffGridIcon.Back, "Back", onBack)
                    Spacer(Modifier.weight(1f))
                    IconButton(OffGridIcon.Edit, "Edit", onEdit, enabled = !vm.busy)
                    IconButton(OffGridIcon.Trash, "Delete", { confirmDelete = true }, enabled = !vm.busy)
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (entry.username.isNotEmpty()) {
                    FieldCard("Username", entry.username) { IconButton(OffGridIcon.Copy, "Copy username", vm::copyUsername, tint = OffGridColors.Ink) }
                }
                PasswordCard(vm)
                if (entry.urls.isNotEmpty() || entry.linkedApps.isNotEmpty()) {
                    SharpCard(Modifier.fillMaxWidth()) {
                        if (entry.urls.isNotEmpty()) {
                            Label("Website", style = OffGridType.LabelSmall)
                            entry.urls.forEach { Body(it, style = OffGridType.BodyStrong) }
                        }
                        if (entry.linkedApps.isNotEmpty()) {
                            Label("Linked apps", style = OffGridType.LabelSmall)
                            entry.linkedApps.forEach { Body(it.package_name, style = OffGridType.Secret) }
                        }
                    }
                }
                if (!entry.notes.isEmpty) NotesCard(vm)
                HistoryCard(entry, vm, onClear = { confirmClear = true })
                Spacer(Modifier.height(72.dp))
            }
        }
        vm.copiedMessage?.let { message ->
            PopButton(
                text = message,
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .fillMaxWidth(),
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            label = "Delete",
            headline = "delete ${entry.title.lowercase().ifEmpty { "this login" }}?",
            body = "This removes the login and its password history for good. There is no recycle bin.",
            confirm = "Delete",
            onCancel = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete(onBack)
            },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            label = "Password history",
            headline = "clear history?",
            body = "Old passwords for this login are deleted for good.",
            confirm = "Clear",
            onCancel = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                vm.clearHistory()
            },
        )
    }
}

@Composable
private fun FieldCard(label: String, value: String, action: @Composable () -> Unit) {
    SharpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(label, style = OffGridType.LabelSmall)
                Body(value, style = OffGridType.BodyStrong)
            }
            action()
        }
    }
}

@Composable
private fun PasswordCard(vm: EntryDetailViewModel) {
    val revealed = vm.revealedPassword
    InverseCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(
                    if (revealed != null) "Password · hides in ${vm.secondsLeft}s" else "Password",
                    color = OffGridColors.Mint,
                    style = OffGridType.LabelSmall,
                )
                Body(revealed ?: MASK, color = OffGridColors.TextOnDark, style = OffGridType.Secret)
            }
            IconButton(OffGridIcon.Eye, if (revealed != null) "Hide password" else "Show password", { vm.toggleReveal(EntryDetailViewModel.Field.Password) }, tint = OffGridColors.Mint)
            IconButton(OffGridIcon.Copy, "Copy password", vm::copyPassword)
        }
        if (revealed != null) {
            Box(
                Modifier
                    .fillWidthFraction(vm.secondsLeft / EntryDetailViewModel.REVEAL_SECONDS.toFloat())
                    .height(3.dp)
                    .background(OffGridColors.Mint),
            )
        }
    }
}

@Composable
private fun NotesCard(vm: EntryDetailViewModel) {
    val revealed = vm.revealedNotes
    SharpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(if (revealed != null) "Notes · hides in ${vm.secondsLeft}s" else "Notes", style = OffGridType.LabelSmall)
                Body(revealed ?: MASK)
            }
            IconButton(OffGridIcon.Eye, if (revealed != null) "Hide notes" else "Show notes", { vm.toggleReveal(EntryDetailViewModel.Field.Notes) }, tint = OffGridColors.Ink)
        }
    }
}

@Composable
private fun HistoryCard(entry: EntryView, vm: EntryDetailViewModel, onClear: () -> Unit) {
    SharpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("History · ${entry.history.size} / ${VaultContent.MAX_HISTORY}", style = OffGridType.LabelSmall, modifier = Modifier.weight(1f))
            if (entry.history.isNotEmpty()) {
                Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClear), contentAlignment = Alignment.Center) {
                    Label("Clear", color = OffGridColors.Green, style = OffGridType.LabelSmall)
                }
            }
        }
        if (entry.history.isEmpty()) Body("no previous passwords.", color = OffGridColors.TextOnLight2)
        val format = DateFormat.getDateInstance(DateFormat.MEDIUM)
        entry.history.forEachIndexed { index, item ->
            val revealed = if (vm.revealedHistoryIndex == index) vm.revealedHistory else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Body(revealed ?: MASK, style = OffGridType.Secret)
                    Label(
                        "replaced " + format.format(Date(item.replacedAtMillis)) + if (revealed != null) " · hides in ${vm.secondsLeft}s" else "",
                        style = OffGridType.LabelSmall,
                    )
                }
                IconButton(OffGridIcon.Eye, if (revealed != null) "Hide previous password" else "Show previous password", { vm.toggleHistory(index) }, tint = OffGridColors.Ink)
                IconButton(OffGridIcon.Copy, "Copy previous password", { vm.copyHistory(index) }, tint = OffGridColors.Ink)
            }
        }
    }
}

/** Dark-band + light-body confirmation (DESIGN_SYSTEM: dialogs use SecureDialog, T2). */
@Composable
internal fun ConfirmDialog(label: String, headline: String, body: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    SecureDialog(onDismissRequest = onCancel) {
        Column {
            HeroBand(headline = headline, label = label, labelColor = OffGridColors.Coral, headlineStyle = OffGridType.HeadlineSheet)
            Column(Modifier.background(OffGridColors.Paper).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Body(body)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onCancel),
                        contentAlignment = Alignment.Center,
                    ) { Label("Cancel", color = OffGridColors.Ink) }
                    PopButton(confirm, onClick = onConfirm, variant = PopVariant.Destructive, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
