package io.github.mnvkalyansambhana.offgridvault.core.vault

/**
 * "Erase vault and start over" (S30). Only ever user-initiated, after the UI has collected the
 * typed confirmation and the phone's screen-lock credential. Order matters: lock the session,
 * delete the device key first (from then on no copy of the vault can be opened), then the files
 * and the attempt counter.
 */
class VaultEraser(
    private val repository: VaultRepository,
    private val attempts: PinAttempts,
    private val session: VaultSession,
    private val deleteDeviceKey: () -> Unit,
) {
    fun eraseEverything() {
        session.lock()
        deleteDeviceKey()
        repository.eraseAll()
        attempts.erase()
    }

    companion object {
        /** The word the user must type (S30). Compared exactly, after trimming. */
        const val CONFIRMATION_WORD = "ERASE"

        fun isConfirmed(typed: String): Boolean = typed.trim() == CONFIRMATION_WORD
    }
}
