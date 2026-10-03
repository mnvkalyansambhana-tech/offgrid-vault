package io.github.mnvkalyansambhana.offgridvault.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/** Serif headline. Copy convention: lowercase, ending with a full stop. */
@Composable
fun Headline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OffGridColors.TextOnDark,
    style: TextStyle = OffGridType.Headline,
) {
    BasicText(text, modifier.semantics { heading() }, style = style.copy(color = color))
}

/** Wide-tracked uppercase label. The text is upper-cased here so callers write normal copy. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OffGridColors.TextOnLight2,
    style: TextStyle = OffGridType.Label,
) {
    BasicText(text.uppercase(), modifier, style = style.copy(color = color))
}

@Composable
fun Body(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OffGridColors.TextOnLight,
    style: TextStyle = OffGridType.Body,
) {
    BasicText(text, modifier, style = style.copy(color = color))
}
