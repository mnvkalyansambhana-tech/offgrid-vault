package io.github.mnvkalyansambhana.offgridvault.ui.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopVariant
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.InverseCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/**
 * "Pop · Locked out" (S3, S6). [hasRecoveryWords] false → the vault stays locked for good; null
 * (header unreadable) → offer recovery anyway.
 */
@Composable
fun LockedOutScreen(hasRecoveryWords: Boolean?, onUseRecoveryWords: () -> Unit, onErase: () -> Unit) {
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(headline = "vault locked.", label = "Security lock", labelColor = OffGridColors.Coral) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("Wrong PIN attempts", color = OffGridColors.TextOnDarkLabel, style = OffGridType.LabelSmall)
                    Label("3 / 3", color = OffGridColors.Coral, style = OffGridType.LabelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { Box(Modifier.weight(1f).height(6.dp).background(OffGridColors.Coral)) }
                }
            }
        },
    ) {
        if (hasRecoveryWords == false) {
            Body("PIN and fingerprint are off, and no recovery words were set up for this vault.")
            InverseCard(Modifier.fillMaxWidth()) {
                Label("This vault stays locked", color = OffGridColors.Coral, style = OffGridType.LabelSmall)
                Body(
                    "Nothing has been deleted, but it can't be opened again. You can erase it and start over.",
                    color = OffGridColors.TextOnDarkBody,
                )
            }
            Spacer(Modifier.weight(1f))
            PopButton("Erase vault & start over", onClick = onErase, variant = PopVariant.Destructive, modifier = Modifier.fillMaxWidth())
        } else {
            Body("PIN and fingerprint are off until you unlock with your 12 recovery words. Then you'll set a new PIN.")
            SharpCard(Modifier.fillMaxWidth()) {
                Label("Your data", color = OffGridColors.Green, style = OffGridType.LabelSmall)
                Body("safe. nothing has been deleted.", style = OffGridType.BodyStrong)
            }
            Spacer(Modifier.weight(1f))
            PopButton("Use recovery words →", onClick = onUseRecoveryWords, modifier = Modifier.fillMaxWidth())
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onErase),
                contentAlignment = Alignment.Center,
            ) { Label("Lost your words? Erase & start over", color = OffGridColors.TextOnLight2, style = OffGridType.LabelSmall) }
        }
    }
}
