package io.github.mnvkalyansambhana.offgridvault.core.vault

import java.io.File

/**
 * Consecutive wrong-PIN counter (S3, S12, S17, S29), in its own small file next to the vault.
 *
 * [recordAttempt] persists the incremented count **before** the PIN is checked, so killing the
 * app mid-check cannot hand out free guesses. Writes are temp file + fsync + atomic rename. An
 * unreadable file fails closed (counts as locked out, S29). No Keystore "protection" is claimed:
 * enforcement relies on the app sandbox, like everything else in scope (S17).
 */
class PinAttempts(private val file: File, private val files: VaultStore.FileOps = VaultStore.FileOps.Real) {

    private val pending = File(file.path + ".tmp")

    fun failures(): Int {
        val bytes = files.read(file) ?: return 0
        val value = bytes.toString(Charsets.US_ASCII).trim().toIntOrNull()
        return if (value == null || value < 0 || value > MAX_STORED) MAX_FAILURES else value
    }

    fun isLockedOut(): Boolean = failures() >= MAX_FAILURES

    /** Call immediately before verifying a PIN. @return the failure count *before* this attempt. */
    fun recordAttempt(): Int {
        val before = failures()
        write(before + 1)
        return before
    }

    /** Undo a [recordAttempt] that never reached a PIN check (e.g. the device key is gone). */
    fun restore(count: Int) = write(count)

    /** S30: part of "erase and start over" — a fresh vault starts with no failures. */
    fun erase() {
        files.delete(pending)
        files.delete(file)
    }

    /** A correct PIN or recovery words (S12). */
    fun reset() = write(0)

    private fun write(count: Int) {
        file.parentFile?.mkdirs()
        files.writeAndSync(pending, count.coerceAtMost(MAX_STORED).toString().toByteArray(Charsets.US_ASCII))
        files.moveAtomically(pending, file)
    }

    companion object {
        /** Wrong PINs before only recovery words unlock (S3). */
        const val MAX_FAILURES = 3
        private const val MAX_STORED = 999
    }
}
