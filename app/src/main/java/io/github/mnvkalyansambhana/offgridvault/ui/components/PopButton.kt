package io.github.mnvkalyansambhana.offgridvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridDimens
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/** Colour sets for [PopButton] (DESIGN_SYSTEM.md §2, §5). */
enum class PopVariant(val face: Color, val content: Color, val edgeBottom: Color, val edgeRight: Color) {
    /** Primary action on a light body. */
    Ink(OffGridColors.Ink, OffGridColors.TextOnDark, OffGridColors.InkEdgeBottom, OffGridColors.InkEdgeRight),

    /** Primary action on a dark band. */
    White(OffGridColors.Card, OffGridColors.Ink, OffGridColors.WhiteEdgeBottom, OffGridColors.WhiteEdgeRight),

    /** Accent action (fingerprint key, Add). */
    Mint(OffGridColors.Mint, OffGridColors.Ink, OffGridColors.MintEdgeBottom, OffGridColors.MintEdgeRight),

    /** Destructive confirmation (Delete). */
    Destructive(
        OffGridColors.CoralDeep,
        OffGridColors.TextOnDark,
        OffGridColors.CoralEdgeBottom,
        OffGridColors.CoralEdgeRight,
    ),
}

/**
 * NeoPOP-style 3D button (T16): a sharp face with bevelled bottom and right edges. While
 * pressed the face moves into the edge space and the edges disappear ("plunk").
 *
 * Space for the edges is reserved around the face, so the button's layout size is the face
 * plus [OffGridDimens.PopDepth] on the right and bottom. Use one per screen, for the main
 * action only (DESIGN_SYSTEM.md §1).
 */
@Composable
fun PopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PopVariant = PopVariant.Ink,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = OffGridDimens.PopDepth
    val depthPx = with(LocalDensity.current) { depth.roundToPx() }
    val face = if (enabled) variant.face else OffGridColors.InputBorder
    val contentColor = if (enabled) variant.content else OffGridColors.TextOnLight2

    Box(
        modifier = modifier.padding(end = depth, bottom = depth),
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier
                .offset { if (pressed) IntOffset(depthPx, depthPx) else IntOffset.Zero }
                .drawBehind { if (enabled && !pressed) drawPopEdges(depth.toPx(), variant) }
                .background(face)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .defaultMinSize(minWidth = OffGridDimens.ButtonHeight, minHeight = OffGridDimens.ButtonHeight)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (content != null) {
                content()
            } else {
                BasicText(text.uppercase(), style = OffGridType.Button.copy(color = contentColor))
            }
        }
    }
}

/** Bottom edge first, then right edge, each a 45° parallelogram [depth] thick. */
private fun DrawScope.drawPopEdges(depth: Float, variant: PopVariant) {
    val w = size.width
    val h = size.height
    val bottom = Path().apply {
        moveTo(0f, h)
        lineTo(w, h)
        lineTo(w + depth, h + depth)
        lineTo(depth, h + depth)
        close()
    }
    val right = Path().apply {
        moveTo(w, 0f)
        lineTo(w + depth, depth)
        lineTo(w + depth, h + depth)
        lineTo(w, h)
        close()
    }
    drawPath(bottom, variant.edgeBottom)
    drawPath(right, variant.edgeRight)
}
