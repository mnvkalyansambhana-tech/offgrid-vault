package io.github.mnvkalyansambhana.offgridvault.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.Biometrics
import io.github.mnvkalyansambhana.offgridvault.core.crypto.BiometricKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.BiometricKeyInvalidatedException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultKeys
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinDots
import io.github.mnvkalyansambhana.offgridvault.ui.components.PinPad
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException

/**
 * "Turn on fingerprint unlock" (S2, M6). Re-asks the PIN first (counts toward the S3 limit), so a
 * borrowed unlocked phone can't add someone else's finger; then a fresh K_bio seals the DEK that
 * the PIN check unwrapped. The DEK is wiped as soon as it is sealed or the flow is left.
 */
class FingerprintSetupViewModel(private val app: AppContainer) : ViewModel() {

    enum class Step { EnterPin, Scan, Saving }

    var step by mutableStateOf(Step.EnterPin)
        private set
    var pinLength by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var wrongPin by mutableStateOf(false)
        private set
    var attemptsLeft by mutableIntStateOf(0)
        private set
    var failed by mutableStateOf(false)
        private set

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
            val result = withContext(Dispatchers.Default) {
                try {
                    app.gate.verify(unlocked.header, pin)
                } finally {
                    pin.wipe()
                }
            }
            busy = false
            when (result) {
                is PinGate.VerifyResult.Correct -> {
                    dek = result.dek
                    step = Step.Scan
                }
                is PinGate.VerifyResult.Wrong -> {
                    wrongPin = true
                    attemptsLeft = result.attemptsLeft
                }
                PinGate.VerifyResult.LockedOut -> app.session.lock() // navigation moves to the lockout screen
            }
        }
    }

    /** A fresh K_bio and its sealing operation for the prompt, or `null` if the key can't be made. */
    fun sealOperation(): BiometricKey.Operation? {
        failed = false
        return try {
            app.biometricKey.create()
            app.biometricKey.sealer()
        } catch (_: GeneralSecurityException) {
            failed = true
            null
        } catch (_: BiometricKeyInvalidatedException) {
            failed = true
            null
        } catch (_: IllegalStateException) {
            failed = true // no strong biometric enrolled any more
            null
        }
    }

    fun onScanned(operation: BiometricKey.Operation, onDone: () -> Unit) {
        val unlocked = app.session.state.value as? VaultSession.State.Unlocked ?: return
        val key = dek ?: return
        step = Step.Saving
        viewModelScope.launch {
            val header = withContext(Dispatchers.Default) {
                runCatching {
                    val sealed = operation.seal(key, VaultKeys.BIO_LABEL)
                    app.biometricEnrollment.enable(unlocked.header, unlocked.content.toVault(), unlocked.key, sealed)
                }.getOrNull()
            }
            if (header == null) {
                app.biometricKey.delete()
                failed = true
                step = Step.Scan
                return@launch
            }
            app.session.updated(header)
            wipeSecrets()
            onDone()
        }
    }

    /** Leaving before the copy was saved: a K_bio without a vault copy is useless. */
    fun cancel() {
        if (step != Step.Saving && app.session.state.value.let { it as? VaultSession.State.Unlocked }
                ?.let { !app.keys.hasBiometric(it.header) } == true
        ) {
            app.biometricKey.delete()
        }
        wipeSecrets()
    }

    private fun clearPin() {
        pinBuffer.wipe()
        pinLength = 0
    }

    private fun wipeSecrets() {
        clearPin()
        dek?.wipe()
        dek = null
    }

    override fun onCleared() = wipeSecrets()
}

@Composable
fun FingerprintSetupScreen(vm: FingerprintSetupViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    val activity = LocalActivity.current
    val leave = {
        vm.cancel()
        onCancel()
    }
    BackHandler { if (vm.step != FingerprintSetupViewModel.Step.Saving) leave() }

    fun scan() {
        val host = activity ?: return
        val operation = vm.sealOperation() ?: return
        Biometrics.authenticate(host, "Turn on fingerprint unlock", "Touch the sensor to confirm", operation.cipher) { cipher ->
            if (cipher != null) vm.onScanned(operation, onDone)
        }
    }

    when (vm.step) {
        FingerprintSetupViewModel.Step.EnterPin -> TwoToneScreen(
            scrollable = false,
            hero = {
                HeroBand(
                    headline = if (vm.wrongPin) "not quite." else "confirm your pin.",
                    label = "Fingerprint unlock",
                    supporting = when {
                        vm.busy -> "checking…"
                        vm.wrongPin -> "Wrong PIN · ${vm.attemptsLeft} left before the vault locks"
                        else -> "only you can turn on fingerprint unlock"
                    },
                ) {
                    PinDots(vm.pinLength, error = vm.wrongPin)
                }
            },
        ) {
            Spacer(Modifier.weight(1f))
            PinPad(onDigit = vm::digit, onDelete = vm::deleteDigit, enabled = !vm.busy)
        }
        FingerprintSetupViewModel.Step.Scan -> {
            LaunchedEffect(Unit) { scan() }
            TwoToneScreen(
                scrollable = false,
                hero = { HeroBand(headline = "touch the sensor.", label = "Fingerprint unlock") },
            ) {
                Body(
                    if (vm.failed) {
                        "That didn't work. Make sure a fingerprint is set up in your phone's settings, then try again."
                    } else {
                        "Any fingerprint enrolled on this phone will open the vault. Adding a new fingerprint " +
                            "later turns fingerprint unlock off until you turn it on again here. After 3 wrong " +
                            "PINs it stops working until you use your recovery words."
                    },
                    color = OffGridColors.TextOnLight2,
                )
                Spacer(Modifier.weight(1f))
                PopButton("Scan fingerprint", onClick = ::scan, modifier = Modifier.fillMaxWidth())
            }
        }
        FingerprintSetupViewModel.Step.Saving -> TwoToneScreen(
            scrollable = false,
            hero = { HeroBand(headline = "saving.", label = "Fingerprint unlock") },
        ) {
            Body("turning on fingerprint unlock…", color = OffGridColors.TextOnLight2)
        }
    }
}
