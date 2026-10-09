package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.IconButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.InverseCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.OffGridIcon
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/**
 * "Pop · Settings".
 * No donation/payment links, ever (CLAUDE.md hard constraint, P3).
 */
@Composable
fun SettingsScreen(
    hasRecoveryWords: Boolean,
    /** null = this phone has no strong biometric and fingerprint unlock is off: row hidden. */
    fingerprintOn: Boolean?,
    onFingerprintToggle: () -> Unit,
    autofillOn: Boolean,
    onAutofill: () -> Unit,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onSetUpRecovery: () -> Unit,
    onLockNow: () -> Unit,
    onAbout: () -> Unit,
) {
    TwoToneScreen(
        hero = { HeroBand(headline = "settings.", navigation = { IconButton(OffGridIcon.Back, "Back", onBack) }) },
    ) {
        Label("Security", style = OffGridType.LabelSmall)
        Group {
            // P20: the recovery-words reminder lives here (plus a dot on the gear).
            SettingsRow(
                text = "recovery words",
                status = if (hasRecoveryWords) "Set up" else "Not set up",
                statusColor = if (hasRecoveryWords) OffGridColors.Green else OffGridColors.Ink,
                statusBackground = if (hasRecoveryWords) null else OffGridColors.Amber,
                onClick = if (hasRecoveryWords) null else onSetUpRecovery,
            )
            if (fingerprintOn != null) {
                Divider()
                SettingsRow(
                    text = "unlock with fingerprint",
                    subtext = "turned off after 3 wrong PINs",
                    status = if (fingerprintOn) "On" else "Off",
                    statusColor = if (fingerprintOn) OffGridColors.Green else OffGridColors.TextOnLight2,
                    onClick = onFingerprintToggle,
                )
            }
            Divider()
            SettingsRow(text = "change PIN", onClick = onChangePin)
            Divider()
            SettingsRow(text = "lock now", onClick = onLockNow)
        }
        if (!hasRecoveryWords) {
            Body("Without recovery words, forgetting your PIN locks this vault for good.", color = OffGridColors.TextOnLight2)
        }
        Spacer(Modifier.height(8.dp))
        Label("Autofill", style = OffGridType.LabelSmall)
        Group {
            SettingsRow(
                text = "fill passwords in other apps",
                subtext = if (autofillOn) "OffGrid Vault is your autofill service" else "choose OffGrid Vault in Android settings",
                status = if (autofillOn) "On" else "Off",
                statusColor = if (autofillOn) OffGridColors.Green else OffGridColors.TextOnLight2,
                onClick = onAutofill,
            )
        }
        Spacer(Modifier.height(8.dp))
        Label("App", style = OffGridType.LabelSmall)
        Group { SettingsRow(text = "about & privacy", onClick = onAbout) }
    }
}

/** "About & privacy": what users expect to find grouped in an app's About page. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    TwoToneScreen(
        hero = {
            HeroBand(headline = "about & privacy.", navigation = { IconButton(OffGridIcon.Back, "Back", onBack) })
        },
    ) {
        Label("Privacy", style = OffGridType.LabelSmall)
        InverseCard(Modifier.fillMaxWidth()) {
            Label("No internet permission", color = OffGridColors.Mint, style = OffGridType.LabelSmall)
            Body(
                "OffGrid Vault can't connect to the internet at all. Check the \"Permissions\" section of its " +
                    "Play Store listing — internet access isn't there.",
                color = OffGridColors.TextOnDarkBody,
            )
        }
        SharpCard(Modifier.fillMaxWidth()) {
            Fact("No data collected", "No account, no analytics, no crash reports, no ads.")
            Fact("Stays on this phone", "Your vault is encrypted and stored only here. No cloud, no backup, no sync.")
            Fact("Uninstall = erased", "Removing the app deletes the vault for good.")
        }
        Label("Open source", style = OffGridType.LabelSmall)
        SharpCard(Modifier.fillMaxWidth()) {
            Fact("License", "GNU GPL v3.0")
            Fact("Source code", "github.com/mnvkalyansambhana-tech/offgrid-vault")
        }
        Label("App", style = OffGridType.LabelSmall)
        SharpCard(Modifier.fillMaxWidth()) {
            Fact("Version", version)
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(OffGridColors.Card).border(1.dp, OffGridColors.PaperHairline)) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(OffGridColors.PaperHairline))
}

@Composable
private fun SettingsRow(
    text: String,
    onClick: (() -> Unit)?,
    subtext: String? = null,
    status: String? = null,
    statusColor: Color = OffGridColors.TextOnLight2,
    statusBackground: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Body(text, style = OffGridType.BodyStrong)
            if (subtext != null) Body(subtext, color = OffGridColors.TextOnLight2)
        }
        if (status != null) {
            Box(Modifier.then(if (statusBackground != null) Modifier.background(statusBackground) else Modifier).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Label(status, color = statusColor, style = OffGridType.LabelSmall)
            }
            Spacer(Modifier.width(8.dp))
        }
        if (onClick != null) Label("›", color = OffGridColors.InputBorder) else Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun Fact(title: String, text: String) {
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(title, style = OffGridType.LabelSmall)
        Body(text, style = OffGridType.BodyStrong)
    }
}
