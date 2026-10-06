package io.github.mnvkalyansambhana.offgridvault.ui.pin

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinDots
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinPad
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors

/**
 * Generic PIN screen ("Pop · Unlock" layout): dark band with headline, dots and an optional
 * coral message; in-app keypad below (S20); optional text link (e.g. "Forgot PIN?").
 */
@Composable
fun PinEntryScreen(
    headline: String,
    label: String,
    supporting: String?,
    pinLength: Int,
    message: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true,
    linkText: String? = null,
    onLink: () -> Unit = {},
) {
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(headline = headline, label = label, supporting = supporting) {
                PinDots(pinLength, error = message != null)
                if (message != null) {
                    Box(Modifier.fillMaxWidth().border(1.5.dp, OffGridColors.Coral).padding(12.dp)) {
                        Body(message, color = OffGridColors.TextOnDarkBody)
                    }
                }
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        PinPad(onDigit = onDigit, onDelete = onDelete, enabled = enabled)
        if (linkText != null) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onLink),
                contentAlignment = Alignment.Center,
            ) {
                Label(linkText, color = OffGridColors.Green)
            }
        }
    }
}

/** The create-and-confirm screen for a new PIN, driven by [NewPinState]. */
@Composable
fun NewPinScreen(state: NewPinState, label: String, onConfirmed: (ByteArray) -> Unit) {
    PinEntryScreen(
        headline = if (state.confirming) "confirm it." else "pick a new pin.",
        label = label,
        supporting = if (state.confirming) "type the same 6 digits again" else "6 digits · different from your screen lock",
        pinLength = state.length,
        message = state.message,
        onDigit = { d -> state.digit(d)?.let(onConfirmed) },
        onDelete = state::delete,
    )
}

/** Shown while a new PIN is being sealed (Argon2 calibration + save, ~1 s). */
@Composable
fun SavingScreen(headline: String, label: String, text: String) {
    TwoToneScreen(scrollable = false, hero = { HeroBand(headline = headline, label = label) }) {
        Body(text, color = OffGridColors.TextOnLight2)
    }
}
