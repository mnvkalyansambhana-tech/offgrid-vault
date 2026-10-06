package io.github.mnvkalyansambhana.offgridvault.ui.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mnvkalyansambhana.offgridvault.AppContainer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import io.github.mnvkalyansambhana.offgridvault.core.crypto.InvalidMnemonicException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinReset.RecoverResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import io.github.mnvkalyansambhana.offgridvault.ui.pin.NewPinState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Forgot PIN / locked out (S3, S6, S28): 12 words → the vault opens → choose a new PIN → sensitive
 * save, attempt counter reset. Nothing is unlocked for the UI until the new PIN is saved.
 */
class RecoverViewModel(private val app: AppContainer) : ViewModel() {

    enum class Step { Words, NewPin, Saving }

    var step by mutableStateOf(Step.Words)
        private set
    val words = mutableStateListOf<String>().apply { repeat(Bip39.WORD_COUNT) { add("") } }
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val newPin = NewPinState()
    private var recovered: RecoverResult.Recovered? = null

    /** Typing several words into one box (e.g. "apple banana …") spreads them over the next boxes. */
    fun setWord(index: Int, value: String) {
        error = null
        val parts = value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size > 1) {
            parts.forEachIndexed { offset, part -> if (index + offset < words.size) words[index + offset] = part.lowercase() }
        } else {
            words[index] = value.trim().lowercase()
        }
    }

    fun isWordOk(index: Int): Boolean = words[index].isEmpty() || Bip39.isValidWord(words[index])

    val allWordsValid: Boolean get() = words.all { it.isNotEmpty() && Bip39.isValidWord(it) }

    fun submitWords() {
        if (!allWordsValid || busy) return
        busy = true
        val typed = words.toList()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    app.pinReset.recover(typed)
                } catch (_: InvalidMnemonicException) {
                    null
                }
            }
            busy = false
            when (result) {
                null -> error = "These words don't fit together — check the order and spelling."
                is RecoverResult.Recovered -> {
                    recovered = result
                    step = Step.NewPin
                }
                RecoverResult.WrongWords -> error = "These words don't match this vault."
                RecoverResult.NoRecoveryWords -> error = "No recovery words were set up for this vault."
                RecoverResult.Unreadable -> error = "The vault file is damaged or was made by a newer version of the app."
                RecoverResult.DeviceKeyLost -> error = "This phone's secure key for the vault is gone."
            }
        }
    }

    fun onNewPin(pin: ByteArray, onDone: () -> Unit) {
        val r = recovered ?: return
        step = Step.Saving
        viewModelScope.launch {
            val header = withContext(Dispatchers.Default) {
                try {
                    app.pinReset.setNewPin(r.opened.header, r.opened.vault, r.opened.key, r.dek, pin)
                } finally {
                    pin.wipe()
                }
            }
            app.session.unlocked(OpenResult.Opened(r.opened.vault, header, r.opened.key, r.opened.restoredFromPrevious))
            r.dek.wipe()
            recovered = null
            clearWords()
            onDone()
        }
    }

    fun back(): Boolean = step == Step.NewPin && newPin.back()

    private fun clearWords() {
        words.indices.forEach { words[it] = "" }
    }

    override fun onCleared() {
        // Abandoned after the words but before a new PIN: close the key, wipe the DEK.
        recovered?.let {
            it.dek.wipe()
            it.opened.key.close()
        }
        recovered = null
        newPin.wipe()
        clearWords()
    }
}
