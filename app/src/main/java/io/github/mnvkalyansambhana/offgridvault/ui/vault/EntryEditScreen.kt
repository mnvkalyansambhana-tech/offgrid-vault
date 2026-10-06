package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.core.vault.PasswordGenerator
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.IconButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.OffGridIcon
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopVariant
import io.github.mnvkalyansambhana.offgridvault.ui.components.SecureDialog
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpInput
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/** "Pop · Edit entry" + "Pop · Generator" (design/screens/pop). */
@Composable
fun EntryEditScreen(vm: EntryEditViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HeroBand(
            headline = vm.title.lowercase().ifBlank { if (vm.isNew) "new login" else "untitled" } + ".",
            label = if (vm.isNew) "New login" else "Edit login",
            navigation = {
                IconButton(OffGridIcon.Close, "Discard", onCancel)
                Spacer(Modifier.weight(1f))
                PopButton(if (vm.saving) "Saving…" else "Save", onClick = { vm.save(onDone) }, variant = PopVariant.White, enabled = !vm.saving)
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SharpInput(vm.title, { vm.title = it }, "Title", Modifier.fillMaxWidth(), isError = vm.error != null)
            vm.error?.let { Label(it, color = OffGridColors.CoralDeep, style = OffGridType.LabelSmall) }
            SharpInput(vm.username, { vm.username = it }, "Username", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SharpInput(
                        vm.password, { vm.password = it }, "Password", Modifier.weight(1f),
                        secret = true, showSecret = vm.showPassword,
                    )
                    IconButton(OffGridIcon.Eye, if (vm.showPassword) "Hide password" else "Show password", { vm.showPassword = !vm.showPassword }, tint = OffGridColors.Ink)
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).background(OffGridColors.Ink).clickable(role = Role.Button, onClick = vm::openGenerator),
                    contentAlignment = Alignment.Center,
                ) { Label("Generate", color = OffGridColors.Mint, style = OffGridType.LabelSmall) }
                if (!vm.isNew) Body("changing it moves the old one to history", color = OffGridColors.TextOnLight2)
            }
            vm.urls.indices.forEach { i ->
                SharpInput(vm.urls[i], { vm.urls[i] = it }, if (i == 0) "Website" else "Website ${i + 1}", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Uri)
            }
            Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = vm::addUrl), contentAlignment = Alignment.CenterStart) {
                Label("+ Add website", color = OffGridColors.Green, style = OffGridType.LabelSmall)
            }
            SharpInput(vm.notes, { vm.notes = it }, "Notes", Modifier.fillMaxWidth(), singleLine = false)
        }
    }
    if (vm.generatorOpen) GeneratorSheet(vm)
}

@Composable
private fun GeneratorSheet(vm: EntryEditViewModel) {
    val o = vm.generatorOptions
    SecureDialog(onDismissRequest = vm::closeGenerator) {
        Column {
            HeroBand(headline = "new password.", label = "Generator", headlineStyle = OffGridType.HeadlineSheet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).background(OffGridColors.InkSurface).border(1.dp, OffGridColors.InkHairline).padding(14.dp)) {
                        BasicText(vm.generated, style = OffGridType.Secret.copy(color = OffGridColors.TextOnDark))
                    }
                    IconButton(OffGridIcon.Refresh, "Generate another", vm::regenerate, tint = OffGridColors.Mint)
                }
                Label("${o.length} chars · ${o.alphabetSize}-char set · ~${o.entropyBits} bits", style = OffGridType.LabelSmall)
            }
            Column(Modifier.background(OffGridColors.Paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Length", style = OffGridType.LabelSmall, modifier = Modifier.weight(1f))
                    Stepper("−") { if (o.length > PasswordGenerator.MIN_LENGTH) vm.updateOptions(o.copy(length = o.length - 1)) }
                    BasicText("${o.length}", style = OffGridType.Secret.copy(color = OffGridColors.Ink), modifier = Modifier.padding(horizontal = 12.dp))
                    Stepper("+") { if (o.length < PasswordGenerator.MAX_LENGTH) vm.updateOptions(o.copy(length = o.length + 1)) }
                }
                Toggle("A–Z", o.upper) { vm.updateOptions(o.copy(upper = it)) }
                Toggle("a–z", o.lower) { vm.updateOptions(o.copy(lower = it)) }
                Toggle("0–9", o.digits) { vm.updateOptions(o.copy(digits = it)) }
                Toggle("symbols", o.symbols) { vm.updateOptions(o.copy(symbols = it)) }
                Toggle("avoid look-alikes", o.avoidLookAlikes) { vm.updateOptions(o.copy(avoidLookAlikes = it)) }
                Spacer(Modifier.size(8.dp))
                PopButton("Use password", onClick = vm::useGenerated, modifier = Modifier.fillMaxWidth())
                Body("fills the form directly — never the clipboard", color = OffGridColors.TextOnLight2)
            }
        }
    }
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).border(1.dp, OffGridColors.Ink).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        BasicText(glyph, style = OffGridType.BodyStrong.copy(color = OffGridColors.Ink))
    }
}

/** Keeps at least one character class on: turning off the last one is ignored. */
@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { runCatching { onChange(it) } }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Body(label, style = OffGridType.BodyStrong, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(22.dp).border(2.dp, OffGridColors.Ink).background(if (checked) OffGridColors.Ink else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { if (checked) BasicText("✓", style = OffGridType.BodyStrong.copy(color = OffGridColors.Mint)) }
        Spacer(Modifier.width(2.dp))
    }
}
