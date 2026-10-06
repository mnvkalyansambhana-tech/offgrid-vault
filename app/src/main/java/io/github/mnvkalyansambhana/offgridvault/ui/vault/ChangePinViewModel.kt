package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate.VerifyResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.ui.pin.NewPinState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Change PIN (S24): the **current PIN** is required (biometric isn't enough) and wrong tries count
 * toward the lockout; a third strike locks the vault on the spot. The new PIN gets a new salt and
 * fresh calibration and is saved twice (C16, C18).
 */
class ChangePinViewModel(private val app: AppContainer) : ViewModel() {

    enum class Step { Current, NewPin, Saving }

    var step by mutableStateOf(Step.Current)
        private set
    var pinLength by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val newPin = NewPinState()
    private val buffer = CharArray(PinPolicy.LENGTH)
    private var dek: ByteArray? = null

    fun digit(d: Char) {
        if (busy || pinLength >= PinPolicy.LENGTH) return
        message = null
        buffer[pinLength++] = d
        if (pinLength == PinPolicy.LENGTH) verifyCurrent()
    }

    fun deleteDigit() {
        if (!busy && pinLength > 0) buffer[--pinLength] = '\u0000'
    }

    private fun verifyCurrent() {
        val unlocked = app.session.state.value as? VaultSession.State.Unlocked ?: return
        busy = true
        val pin = ByteArray(PinPolicy.LENGTH) { buffer[it].code.toByte() }
        clear()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    app.gate.verify(unlocked.header, pin)
                } finally {
                    pin.wipe()
                }
            }
            busy = false
            when (result) {
                is VerifyResult.Correct -> {
                    dek = result.dek
                    step = Step.NewPin
                }
                is VerifyResult.Wrong -> message = "Wrong PIN · ${result.attemptsLeft} left before the vault locks"
                VerifyResult.LockedOut -> app.session.lock() // navigation moves to the lockout screen
            }
        }
    }

    fun onNewPin(pin: ByteArray, onDone: () -> Unit) {
        val unlocked = app.session.state.value as? VaultSession.State.Unlocked ?: return
        val key = dek ?: return
        step = Step.Saving
        viewModelScope.launch {
            val header = withContext(Dispatchers.Default) {
                try {
                    app.pinReset.setNewPin(unlocked.header, unlocked.content.toVault(), unlocked.key, key, pin)
                } finally {
                    pin.wipe()
                }
            }
            app.session.updated(header)
            wipe()
            onDone()
        }
    }

    fun back(): Boolean = step == Step.NewPin && newPin.back()

    private fun clear() {
        buffer.wipe()
        pinLength = 0
    }

    private fun wipe() {
        clear()
        dek?.wipe()
        dek = null
        newPin.wipe()
    }

    override fun onCleared() = wipe()
}
