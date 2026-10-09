package io.github.mnvkalyansambhana.offgridvault.ui.unlock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.BiometricKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.BiometricKeyInvalidatedException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DecryptionFailedException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate.UnlockResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PIN unlock through [io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate] (S3, S12, S17, S22),
 * plus fingerprint unlock when it is on (S2, M6) — never once the PIN attempts are exhausted (S3).
 */
class UnlockViewModel(private val app: AppContainer) : ViewModel() {

    sealed interface Problem {
        data class WrongPin(val attemptsLeft: Int) : Problem
        data object NoScreenLock : Problem
        data object DeviceKeyLost : Problem
        data object Unreadable : Problem
        /** K_bio was invalidated by a newly added fingerprint (setInvalidatedByBiometricEnrollment). */
        data object FingerprintChanged : Problem
        /** The fingerprint copy didn't open the vault; it was turned off. */
        data object FingerprintFailed : Problem
    }

    var pinLength by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf<Problem?>(null)
        private set
    /** Show the fingerprint key (and prompt automatically on arrival). */
    var fingerprintReady by mutableStateOf(false)
        private set

    private val pinBuffer = CharArray(PinPolicy.LENGTH)

    fun refresh() {
        if (!app.isDeviceSecure()) problem = Problem.NoScreenLock
        else if (problem == Problem.NoScreenLock) problem = null
        fingerprintReady = problem != Problem.NoScreenLock &&
            app.hasStrongBiometric() &&
            app.biometricKey.exists() &&
            app.repository.peekHeader()?.let(app.keys::hasBiometric) == true &&
            !app.gate.isLockedOut()
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

    /**
     * Step 1 of fingerprint unlock: a K_bio operation for the prompt's CryptoObject, or `null` when
     * fingerprint unlock can't be used (a new fingerprint invalidated K_bio → turned off).
     */
    fun fingerprintOperation(): BiometricKey.Operation? {
        if (!fingerprintReady || busy) return null
        val blob = app.repository.peekHeader()?.wrapped_key_bio?.toByteArray() ?: return null
        return try {
            app.biometricKey.opener(blob)
        } catch (_: BiometricKeyInvalidatedException) {
            turnFingerprintOff(Problem.FingerprintChanged)
            null
        } catch (_: DecryptionFailedException) {
            turnFingerprintOff(Problem.FingerprintFailed)
            null
        }
    }

    /** Step 2, after the prompt succeeded: K_bio opens the DEK copy and the vault is opened with it. */
    fun onFingerprintSuccess(operation: BiometricKey.Operation, nav: Navigation) {
        if (busy) return
        busy = true
        clearPin()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val dek = try {
                    operation.open(VaultKeys.BIO_LABEL)
                } catch (_: DecryptionFailedException) {
                    null
                }
                if (dek == null) UnlockResult.BiometricRejected else app.gate.unlockWithBiometric(dek)
            }
            busy = false
            handle(result, nav)
        }
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
            handle(result, nav)
        }
    }

    private suspend fun handle(result: UnlockResult, nav: Navigation) {
        when (result) {
            is UnlockResult.Unlocked -> {
                app.session.unlocked(result.opened, result.previousFailures)
                busy = true
                withContext(Dispatchers.IO) { app.dropStaleFingerprint() }
                busy = false
                nav.onUnlocked()
            }
            is UnlockResult.Wrong -> problem = Problem.WrongPin(result.attemptsLeft)
            UnlockResult.LockedOut -> nav.onLockedOut()
            UnlockResult.NoVault -> nav.onNoVault()
            UnlockResult.Unreadable -> problem = Problem.Unreadable
            UnlockResult.DeviceKeyLost -> problem = Problem.DeviceKeyLost
            UnlockResult.BiometricRejected -> turnFingerprintOff(Problem.FingerprintFailed)
        }
    }

    /** Deletes K_bio; the vault's now-useless copy is removed after the next PIN unlock. */
    private fun turnFingerprintOff(reason: Problem) {
        app.biometricKey.delete()
        fingerprintReady = false
        problem = reason
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
