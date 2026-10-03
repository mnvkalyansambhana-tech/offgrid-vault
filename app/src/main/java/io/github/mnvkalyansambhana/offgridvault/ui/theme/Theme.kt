package io.github.mnvkalyansambhana.offgridvault.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Spacing and sizing tokens from docs/DESIGN_SYSTEM.md §4. */
object OffGridDimens {
    val ScreenPadding = 24.dp
    val PopDepth = 6.dp
    val ButtonHeight = 56.dp
    val TouchTarget = 44.dp
    val Hairline = 1.dp
    val InputBorder = 1.5.dp
}

/**
 * Root of every screen. OffGrid Pop is a single two-tone look (T15), so there is no light/dark
 * switching: tokens are read directly from [OffGridColors] and [OffGridType].
 */
@Composable
fun OffGridTheme(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(OffGridColors.Paper)) {
        content()
    }
}
