package io.github.mnvkalyansambhana.offgridvault.ui.components

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * S20 / T1: tells the keyboard not to learn from, or suggest, anything typed inside [content].
 *
 * Compose (1.12) never sets `IME_FLAG_NO_PERSONALIZED_LEARNING`, and with autocorrect off it
 * still omits `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (checked in its EditorInfo code during M0), so
 * the flags are added here by intercepting the text input request. Keyboards treat these as a
 * request; well-behaved ones (Gboard, SwiftKey) honour them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoKeyboardLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val private = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also { hardenEditorInfo(outAttributes) }
            }
            nextHandler.startInputMethod(private)
        },
        content = content,
    )
}

private fun hardenEditorInfo(info: EditorInfo) {
    info.imeOptions = info.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    if (info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
        info.inputType = (info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT.inv()) or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
}
