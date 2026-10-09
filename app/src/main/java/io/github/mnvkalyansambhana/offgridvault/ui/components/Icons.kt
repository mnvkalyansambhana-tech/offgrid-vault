package io.github.mnvkalyansambhana.offgridvault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors

/** Stroke icons with square caps (DESIGN_SYSTEM §6), drawn on a 24-unit grid. No emoji. */
enum class OffGridIcon { Lock, Gear, Eye, Copy, Back, Edit, Trash, Close, Refresh, Fingerprint, Search }

@Composable
fun IconButton(
    icon: OffGridIcon,
    description: String,
    onClick: () -> Unit,
    tint: Color = OffGridColors.TextOnDark,
    enabled: Boolean = true,
    /** Small amber dot: "something here needs your attention" (P20). */
    badge: Boolean = false,
) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (badge) "$description, needs attention" else description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, tint)
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 9.dp)
                    .size(8.dp)
                    .background(OffGridColors.Amber),
            )
        }
    }
}

@Composable
fun Icon(icon: OffGridIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Square, join = StrokeJoin.Miter)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(vararg pts: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(pts[0].first * u, pts[0].second * u)
                pts.drop(1).forEach { (x, y) -> lineTo(x * u, y * u) }
            }
            drawPath(path, tint, style = stroke)
        }
        when (icon) {
            OffGridIcon.Lock -> {
                drawRect(tint, topLeft = p(5f, 11f), size = Size(14f * u, 10f * u), style = stroke)
                line(8f to 11f, 8f to 7f)
                line(16f to 11f, 16f to 7f)
                drawArc(tint, 180f, 180f, false, topLeft = p(8f, 3f), size = Size(8f * u, 8f * u), style = stroke)
            }
            OffGridIcon.Gear -> {
                // Toothed wheel: 8 square teeth around a ring, hollow centre.
                val gear = Path()
                for (j in 0 until 32) {
                    val angle = Math.toRadians(j * 11.25 - 5.625)
                    val radius = if (j % 4 == 1 || j % 4 == 2) 10.5f else 7.5f
                    val x = (12f + radius * kotlin.math.cos(angle).toFloat()) * u
                    val y = (12f + radius * kotlin.math.sin(angle).toFloat()) * u
                    if (j == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
                }
                gear.close()
                drawPath(gear, tint, style = stroke)
                drawCircle(tint, radius = 3f * u, center = p(12f, 12f), style = stroke)
            }
            OffGridIcon.Eye -> {
                eye(tint, stroke, u)
                drawCircle(tint, radius = 3f * u, center = p(12f, 12f), style = stroke)
            }
            OffGridIcon.Copy -> {
                drawRect(tint, topLeft = p(9f, 9f), size = Size(11f * u, 11f * u), style = stroke)
                line(5f to 15f, 5f to 4f, 16f to 4f)
            }
            OffGridIcon.Back -> line(15f to 18f, 9f to 12f, 15f to 6f)
            OffGridIcon.Edit -> line(4f to 20f, 8f to 20f, 19f to 9f, 15f to 5f, 4f to 16f, 4f to 20f)
            OffGridIcon.Trash -> {
                line(4f to 7f, 20f to 7f)
                line(6f to 7f, 7f to 20f, 17f to 20f, 18f to 7f)
                line(9f to 7f, 9f to 4f, 15f to 4f, 15f to 7f)
                line(10f to 11f, 10f to 17f); line(14f to 11f, 14f to 17f)
            }
            OffGridIcon.Close -> {
                line(6f to 6f, 18f to 18f); line(18f to 6f, 6f to 18f)
            }
            OffGridIcon.Fingerprint -> {
                // Nested ridges (design/screens/pop/PopUnlock fingerprint key).
                drawArc(tint, 180f, 180f, false, topLeft = p(4f, 2f), size = Size(16f * u, 16f * u), style = stroke)
                line(4f to 10f, 4f to 13f); line(20f to 10f, 20f to 13f)
                drawArc(tint, 180f, 180f, false, topLeft = p(7f, 6f), size = Size(10f * u, 10f * u), style = stroke)
                line(17f to 11f, 17f to 13f)
                line(12f to 11f, 12f to 15f, 10.5f to 19f)
                line(8f to 15f, 7.5f to 19f)
                line(16f to 15f, 15.2f to 19.5f)
            }
            OffGridIcon.Search -> {
                drawCircle(tint, radius = 6.5f * u, center = p(10.5f, 10.5f), style = stroke)
                line(15.5f to 15.5f, 20.5f to 20.5f)
            }
            OffGridIcon.Refresh -> {
                drawArc(tint, -40f, 300f, false, topLeft = p(4f, 4f), size = Size(16f * u, 16f * u), style = stroke)
                line(20f to 4f, 20f to 9f, 15f to 9f)
            }
        }
    }
}

private fun DrawScope.eye(tint: Color, stroke: Stroke, u: Float) {
    val path = Path().apply {
        moveTo(2f * u, 12f * u)
        quadraticTo(12f * u, 2f * u, 22f * u, 12f * u)
        quadraticTo(12f * u, 22f * u, 2f * u, 12f * u)
        close()
    }
    drawPath(path, tint, style = stroke)
}
