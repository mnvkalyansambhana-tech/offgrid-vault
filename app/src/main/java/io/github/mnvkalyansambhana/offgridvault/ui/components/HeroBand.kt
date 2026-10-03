package io.github.mnvkalyansambhana.offgridvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridDimens
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/**
 * The dark top band every screen starts with (DESIGN_SYSTEM.md §1): optional navigation row,
 * label and serif headline, then any extra content (PIN dots, status strips…).
 * Draws under the status bar and pads its content below it.
 */
@Composable
fun HeroBand(
    headline: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelColor: Color = OffGridColors.TextOnDarkLabel,
    supporting: String? = null,
    headlineStyle: TextStyle = OffGridType.Headline,
    navigation: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OffGridColors.Ink)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(
                start = OffGridDimens.ScreenPadding,
                end = OffGridDimens.ScreenPadding,
                top = if (navigation != null) 8.dp else 48.dp,
                bottom = 28.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (navigation != null) {
            // Icon buttons have 44dp targets; pull them left so the glyph aligns with the text.
            Row(Modifier.offset(x = (-12).dp), content = navigation)
        }
        if (label != null) Label(label, color = labelColor)
        Headline(headline, style = headlineStyle)
        if (supporting != null) Body(supporting, color = OffGridColors.TextOnDarkBody)
        content()
    }
}
