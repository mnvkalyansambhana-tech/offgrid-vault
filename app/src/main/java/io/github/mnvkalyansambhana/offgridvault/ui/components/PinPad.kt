package io.github.mnvkalyansambhana.offgridvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridFonts
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Six square PIN dots (DESIGN_SYSTEM §5). [error] outlines them in coral. */
@Composable
fun PinDots(filled: Int, modifier: Modifier = Modifier, length: Int = 6, error: Boolean = false) {
    Row(modifier.semantics { contentDescription = "$filled of $length digits entered" }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(length) { i ->
            val on = i < filled
            val border = if (error) OffGridColors.Coral else if (on) OffGridColors.TextOnDark else Color(0xFF5A5A5A)
            Box(
                Modifier
                    .size(16.dp)
                    .then(if (on && !error) Modifier.background(OffGridColors.TextOnDark) else Modifier)
                    .border(2.dp, border),
            )
        }
    }
}

/**
 * In-app PIN keypad (S20): the system keyboard never sees the PIN. 3×4 grid; the bottom-left
 * slot is for the fingerprint key (M6) and is empty when [bottomLeft] is null.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bottomLeft: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d -> DigitKey(d, enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bottomLeft != null) bottomLeft() else Box(Modifier.weight(1f))
            DigitKey('0', enabled) { onDigit('0') }
            Box(
                Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = "Delete digit" },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("⌫", style = TextStyle(fontSize = 24.sp, color = OffGridColors.Ink))
            }
        }
    }
}

@Composable
private fun RowScope.DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(60.dp)
            .background(OffGridColors.Card)
            .border(1.dp, OffGridColors.PaperHairline)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            digit.toString(),
            style = OffGridType.Body.copy(
                fontFamily = OffGridFonts.Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = OffGridColors.Ink,
            ),
        )
    }
}
