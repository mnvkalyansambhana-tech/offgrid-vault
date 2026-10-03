package io.github.mnvkalyansambhana.offgridvault.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridDimens
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridTheme
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/**
 * M0 shell: the locked "Pop · Welcome" screen (design/screens/pop/PopWelcome.dc.html).
 * Setup and the screen-lock check (S22) arrive in M3.
 */
@Composable
fun WelcomeScreen(onSetUp: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OffGridColors.Ink)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).border(1.5.dp, OffGridColors.TextOnDark))
                Spacer(Modifier.width(12.dp))
                Label("OffGrid Vault", color = OffGridColors.TextOnDark)
            }
            BasicText(
                text = buildAnnotatedString {
                    append("your passwords.\non this phone.\n")
                    withStyle(SpanStyle(color = OffGridColors.Mint)) { append("nowhere else.") }
                },
                style = OffGridType.HeadlineHero.copy(color = OffGridColors.TextOnDark),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(OffGridDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Feature("01", "no internet permission")
            Feature("02", "no account. no cloud. no tracking.")
            Feature("03", "uninstall = vault erased forever", OffGridColors.CoralDeep)
            Spacer(Modifier.weight(1f))
            SharpCard(Modifier.fillMaxWidth()) {
                Label("Phone screen lock · checked at setup", style = OffGridType.LabelSmall)
            }
            PopButton(text = "Set up vault →", onClick = onSetUp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Feature(number: String, text: String, color: Color = OffGridColors.TextOnLight) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Body(number, color = OffGridColors.TextOnLight2, style = OffGridType.Secret)
        Body(text, color = color, style = OffGridType.BodyStrong)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun WelcomePreview() {
    OffGridTheme { WelcomeScreen(onSetUp = {}) }
}
