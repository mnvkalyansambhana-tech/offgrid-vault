package io.github.mnvkalyansambhana.offgridvault.ui.unlock

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
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PIN unlock, happy path (M3). Attempt counting and the 3-strike lockout (S3, S12, S17) are
 * M4; recovery words are M4; biometric is M6.
 */
class UnlockViewModel(private val app: AppContainer) : ViewModel() {

    enum class Problem { WrongPin, NoScreenLock, DeviceKeyLost, Unreadable }

    var pinLength by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf<Problem?>(null)
        private set

    private val pinBuffer = CharArray(PinPolicy.LENGTH)

    fun refresh() {
        if (!app.isDeviceSecure()) problem = Problem.NoScreenLock
        else if (problem == Problem.NoScreenLock) problem = null
    }

    fun digit(d: Char, onUnlocked: () -> Unit, onNoVault: () -> Unit) {
        if (busy || pinLength >= PinPolicy.LENGTH || problem == Problem.NoScreenLock) return
        if (problem == Problem.WrongPin) problem = null
        pinBuffer[pinLength++] = d
        if (pinLength == PinPolicy.LENGTH) verify(onUnlocked, onNoVault)
    }

    fun deleteDigit() {
        if (!busy && pinLength > 0) pinBuffer[--pinLength] = '\u0000'
    }

    private fun verify(onUnlocked: () -> Unit, onNoVault: () -> Unit) {
        busy = true
        val pin = ByteArray(PinPolicy.LENGTH) { pinBuffer[it].code.toByte() }
        clearPin()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    app.repository.open { header -> app.keys.unwrapWithPin(header, pin) }
                } catch (_: DeviceKeyUnavailableException) {
                    null
                } finally {
                    pin.wipe()
                }
            }
            busy = false
            when (result) {
                is OpenResult.Opened -> {
                    app.session.unlocked(result)
                    onUnlocked()
                }
                OpenResult.Rejected -> problem = Problem.WrongPin
                OpenResult.NoVault -> onNoVault()
                is OpenResult.Unreadable -> problem = Problem.Unreadable
                null -> problem = Problem.DeviceKeyLost
            }
        }
    }

    private fun clearPin() {
        pinBuffer.wipe()
        pinLength = 0
    }

    override fun onCleared() = clearPin()
}
