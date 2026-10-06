package io.github.mnvkalyansambhana.offgridvault.ui.pin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy

/**
 * "pick a pin." → "confirm it." (P18) with the S19/S27 blocklist. Used by recovery (new PIN after
 * the words) and Change PIN (S24). Digits live only in wiped char arrays.
 */
class NewPinState {

    var confirming by mutableStateOf(false)
        private set
    var length by mutableIntStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private val buffer = CharArray(PinPolicy.LENGTH)
    private var first: CharArray? = null

    /** @return the confirmed PIN as ASCII bytes (caller wipes), or null while still entering. */
    fun digit(d: Char): ByteArray? {
        if (length >= PinPolicy.LENGTH) return null
        message = null
        buffer[length++] = d
        if (length < PinPolicy.LENGTH) return null
        val entered = buffer.copyOf()
        clearBuffer()
        if (!confirming) {
            if (PinPolicy.check(entered) != null) {
                entered.wipe()
                message = "Too easy to guess — no repeats, runs like 123456 or common PINs"
            } else {
                first = entered
                confirming = true
            }
            return null
        }
        val matches = entered.contentEquals(first)
        val result = if (matches) ByteArray(entered.size) { entered[it].code.toByte() } else null
        entered.wipe()
        if (!matches) {
            message = "PINs didn't match — pick your PIN again"
            reset()
        }
        return result
    }

    fun delete() {
        if (length > 0) buffer[--length] = '\u0000'
    }

    /** Back from "confirm it." to "pick a pin.". @return true if handled. */
    fun back(): Boolean {
        if (!confirming) return false
        reset()
        return true
    }

    fun wipe() {
        reset()
        message = null
    }

    private fun reset() {
        first?.wipe()
        first = null
        confirming = false
        clearBuffer()
    }

    private fun clearBuffer() {
        buffer.wipe()
        length = 0
    }
}
