package io.github.mnvkalyansambhana.offgridvault.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKeyUnavailableException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy
import io.github.mnvkalyansambhana.offgridvault.ui.recovery.RecoveryWordsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-run setup (P17, P18, P19, S19, S22, S28). PIN and recovery entropy live only here, in
 * memory, until "I understand" (or "Set up later") creates the vault; they are wiped right after,
 * or when the user leaves setup. Process death mid-setup simply restarts it — nothing was written.
 */
class SetupViewModel(private val app: AppContainer) : ViewModel() {

    enum class Step { Welcome, CreatePin, ConfirmPin, Recovery, Sealing }

    var step by mutableStateOf(Step.Welcome)
        private set
    var deviceSecure by mutableStateOf(app.isDeviceSecure())
        private set
    var pinLength by mutableIntStateOf(0)
        private set
    var pinMessage by mutableStateOf<String?>(null)
        private set
    var askSkipConfirmation by mutableStateOf(false)
        private set
    var sealingFailed by mutableStateOf(false)
        private set

    val recovery = RecoveryWordsState()

    private val pinBuffer = CharArray(PinPolicy.LENGTH)
    private var chosenPin: CharArray? = null
    private var skipRecovery = false

    fun refreshDeviceSecure() {
        deviceSecure = app.isDeviceSecure()
    }

    fun start() {
        refreshDeviceSecure()
        if (deviceSecure) step = Step.CreatePin
    }

    fun digit(d: Char) {
        if (pinLength >= PinPolicy.LENGTH) return
        pinMessage = null
        pinBuffer[pinLength++] = d
        if (pinLength == PinPolicy.LENGTH) onPinComplete()
    }

    fun deleteDigit() {
        if (pinLength > 0) pinBuffer[--pinLength] = '\u0000'
    }

    private fun onPinComplete() {
        val entered = pinBuffer.copyOf()
        clearPinBuffer()
        when (step) {
            Step.CreatePin -> {
                if (PinPolicy.check(entered) != null) {
                    entered.wipe()
                    pinMessage = "Too easy to guess — no repeats, runs like 123456 or common PINs"
                } else {
                    chosenPin = entered
                    step = Step.ConfirmPin
                }
            }
            Step.ConfirmPin -> {
                val matches = entered.contentEquals(chosenPin)
                entered.wipe()
                if (matches) {
                    step = Step.Recovery
                } else {
                    chosenPin?.wipe()
                    chosenPin = null
                    pinMessage = "PINs didn't match — pick your PIN again"
                    step = Step.CreatePin
                }
            }
            else -> entered.wipe()
        }
    }

    fun requestSkip() {
        askSkipConfirmation = true
    }

    fun cancelSkip() {
        askSkipConfirmation = false
    }

    /** P19: create the vault without recovery words; the vault then shows the amber banner. */
    fun confirmSkip(onCreated: () -> Unit) {
        askSkipConfirmation = false
        recovery.wipe()
        skipRecovery = true
        finish(onCreated)
    }

    /** P17: the vault is created only now. */
    fun finish(onCreated: () -> Unit) {
        val pin = chosenPin ?: return
        val entropy = if (skipRecovery) null else (recovery.entropy() ?: return)
        step = Step.Sealing
        sealingFailed = false
        viewModelScope.launch {
            val opened = withContext(Dispatchers.Default) {
                val pinBytes = ByteArray(pin.size) { pin[it].code.toByte() }
                try {
                    val params = app.calibrator.calibrate().params
                    app.setup.create(pinBytes, entropy, params)
                } catch (_: DeviceKeyUnavailableException) {
                    null
                } finally {
                    pinBytes.wipe()
                }
            }
            if (opened == null) {
                sealingFailed = true
                return@launch
            }
            app.session.unlocked(opened)
            wipeSecrets()
            onCreated()
        }
    }

    fun back(): Boolean {
        when (step) {
            Step.CreatePin -> step = Step.Welcome
            Step.ConfirmPin -> {
                chosenPin?.wipe()
                chosenPin = null
                clearPinBuffer()
                step = Step.CreatePin
            }
            Step.Recovery -> return recovery.back()
            else -> return false
        }
        return true
    }

    private fun clearPinBuffer() {
        pinBuffer.wipe()
        pinLength = 0
    }

    private fun wipeSecrets() {
        clearPinBuffer()
        chosenPin?.wipe()
        chosenPin = null
        recovery.wipe()
    }

    override fun onCleared() {
        wipeSecrets()
    }
}
