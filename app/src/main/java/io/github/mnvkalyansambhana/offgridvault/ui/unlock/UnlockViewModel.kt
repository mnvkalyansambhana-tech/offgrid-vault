package io.github.mnvkalyansambhana.offgridvault.ui.unlock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate.UnlockResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** PIN unlock through [io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate] (S3, S12, S17, S22). */
class UnlockViewModel(private val app: AppContainer) : ViewModel() {

    sealed interface Problem {
        data class WrongPin(val attemptsLeft: Int) : Problem
        data object NoScreenLock : Problem
        data object DeviceKeyLost : Problem
        data object Unreadable : Problem
    }

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

    fun digit(d: Char, nav: Navigation) {
        if (busy || pinLength >= PinPolicy.LENGTH || problem == Problem.NoScreenLock) return
        if (problem is Problem.WrongPin) problem = null
        pinBuffer[pinLength++] = d
        if (pinLength == PinPolicy.LENGTH) verify(nav)
    }

    fun deleteDigit() {
        if (!busy && pinLength > 0) pinBuffer[--pinLength] = '\u0000'
    }

    private fun verify(nav: Navigation) {
        busy = true
        val pin = ByteArray(PinPolicy.LENGTH) { pinBuffer[it].code.toByte() }
        clearPin()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    app.gate.unlock(pin)
                } finally {
                    pin.wipe()
                }
            }
            busy = false
            when (result) {
                is UnlockResult.Unlocked -> {
                    app.session.unlocked(result.opened, result.previousFailures)
                    nav.onUnlocked()
                }
                is UnlockResult.Wrong -> problem = Problem.WrongPin(result.attemptsLeft)
                UnlockResult.LockedOut -> nav.onLockedOut()
                UnlockResult.NoVault -> nav.onNoVault()
                UnlockResult.Unreadable -> problem = Problem.Unreadable
                UnlockResult.DeviceKeyLost -> problem = Problem.DeviceKeyLost
            }
        }
    }

    private fun clearPin() {
        pinBuffer.wipe()
        pinLength = 0
    }

    override fun onCleared() = clearPin()

    interface Navigation {
        fun onUnlocked()
        fun onLockedOut()
        fun onNoVault()
    }
}
