package io.github.mnvkalyansambhana.offgridvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridDimens
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/**
 * Sharp labelled text input (DESIGN_SYSTEM.md §5). Border: idle grey, focused ink, error coral.
 *
 * All fields ask the keyboard not to learn or suggest ([NoKeyboardLearning], S20). [secret]
 * fields are also masked and use a password keyboard. The value lives in a Kotlin `String`,
 * which cannot be wiped (best effort, S25).
 */
@Composable
fun SharpInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> OffGridColors.CoralDeep
        focused -> OffGridColors.Ink
        else -> OffGridColors.InputBorder
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label, style = OffGridType.LabelSmall)
        // Every field in a password manager is sensitive: no keyboard learning anywhere (S20).
        NoKeyboardLearning {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .background(OffGridColors.Card)
                    .border(OffGridDimens.InputBorder, borderColor)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                textStyle = (if (secret) OffGridType.Secret else OffGridType.Body).copy(color = OffGridColors.Ink),
                singleLine = true,
                cursorBrush = SolidColor(OffGridColors.Ink),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = if (secret) KeyboardType.Password else keyboardType,
                    capitalization = capitalization,
                ),
            )
        }
    }
}
