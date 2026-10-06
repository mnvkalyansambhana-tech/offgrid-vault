package io.github.mnvkalyansambhana.offgridvault.ui.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Set up recovery words" from the vault banner (P19, S28). Re-asks the PIN first, so someone
 * holding an already-unlocked phone cannot mint their own recovery words. The DEK unwrapped by
 * that PIN check is what the words get bound to; it is wiped as soon as they are saved.
 */
class RecoveryLaterViewModel(private val app: AppContainer) : ViewModel() {

    enum class Step { EnterPin, Words, Saving }

    var step by mutableStateOf(Step.EnterPin)
        private set
    var pinLength by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var wrongPin by mutableStateOf(false)
        private set

    val recovery = RecoveryWordsState()

    private val pinBuffer = CharArray(PinPolicy.LENGTH)
    private var dek: ByteArray? = null

    fun digit(d: Char) {
        if (busy || pinLength >= PinPolicy.LENGTH) return
        wrongPin = false
        pinBuffer[pinLength++] = d
        if (pinLength == PinPolicy.LENGTH) verifyPin()
    }

    fun deleteDigit() {
        if (!busy && pinLength > 0) pinBuffer[--pinLength] = '\u0000'
    }

    private fun verifyPin() {
        val unlocked = app.session.state.value as? VaultSession.State.Unlocked ?: return
        busy = true
        val pin = ByteArray(PinPolicy.LENGTH) { pinBuffer[it].code.toByte() }
        clearPin()
        viewModelScope.launch {
            // M4: these attempts must count toward the S3 lockout too.
            val unwrapped = withContext(Dispatchers.Default) {
                try {
                    app.keys.unwrapWithPin(unlocked.header, pin)
                } finally {
                    pin.wipe()
                }
            }
            busy = false
            if (unwrapped == null) {
                wrongPin = true
            } else {
                dek = unwrapped
                step = Step.Words
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val unlocked = app.session.state.value as? VaultSession.State.Unlocked ?: return
        val key = dek ?: return
        val entropy = recovery.entropy() ?: return
        step = Step.Saving
        viewModelScope.launch {
            val header = withContext(Dispatchers.Default) {
                app.enrollment.enroll(unlocked.header, unlocked.vault, unlocked.key, key, entropy)
            }
            app.session.updated(header, unlocked.vault)
            wipeSecrets()
            onDone()
        }
    }

    fun back(): Boolean = step == Step.Words && recovery.back()

    private fun clearPin() {
        pinBuffer.wipe()
        pinLength = 0
    }

    private fun wipeSecrets() {
        clearPin()
        dek?.wipe()
        dek = null
        recovery.wipe()
    }

    override fun onCleared() = wipeSecrets()
}
