package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DecryptionFailedException
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader

/**
 * Opens and saves the vault (C5, C16–C18). Unlocking — turning a PIN, biometric or recovery
 * words into the DEK using the header's wrapped keys — is supplied by the caller (M3).
 */
class VaultRepository(private val store: VaultStore) {

    /**
     * Turns the (not yet authenticated) header into the 32-byte DEK, or `null` if the credential
     * is rejected. May throw [CorruptVaultException] (that file is damaged) or
     * DeviceKeyUnavailableException (propagates: the vault cannot be opened on this device).
     */
    fun interface Unlocker {
        fun unlock(header: VaultHeader): ByteArray?
    }

    sealed interface OpenResult {
        /**
         * @property key the DEK for this session; the caller must [AeadKey.close] it on lock.
         * @property restoredFromPrevious `vault.bin` was unreadable and `vault.prev` was used —
         *   show "Restored from previous save — your last change may be missing" (C18). Both
         *   files have already been re-saved from the restored copy.
         */
        class Opened(
            val vault: Vault,
            val header: VaultHeader,
            val key: AeadKey,
            val restoredFromPrevious: Boolean,
        ) : OpenResult

        /** No vault on this device: run setup. */
        data object NoVault : OpenResult

        /** PIN / biometric / recovery words rejected. Attempt counting is the caller's job (S12). */
        data object Rejected : OpenResult

        /** Neither file could be read: damaged storage or a newer app's format. */
        data class Unreadable(val newerFormatVersion: Int? = null) : OpenResult
    }

    /** Whether a vault exists on this device (setup completed, P17). */
    fun hasVault(): Boolean {
        store.recoverInterruptedSave()
        return store.hasAnyVault()
    }

    /**
     * The header of the current (else previous) file, parsed but **not authenticated** — for UI
     * decisions only (e.g. "are recovery words set up?"), never for security decisions.
     */
    fun peekHeader(): VaultHeader? {
        store.recoverInterruptedSave()
        for (bytes in listOf(store.readCurrent(), store.readPrevious())) {
            bytes ?: continue
            try {
                return VaultFormat.parse(bytes).header
            } catch (_: CorruptVaultException) {
            } catch (_: UnsupportedVaultVersionException) {
            }
        }
        return null
    }

    /** S30 "erase and start over": the vault files are gone; [hasVault] becomes false. */
    fun eraseAll() = store.eraseAll()

    fun open(unlocker: Unlocker): OpenResult {
        store.recoverInterruptedSave()
        if (!store.hasAnyVault()) return OpenResult.NoVault

        val current = attempt(store.readCurrent(), unlocker, skipUnlockIfSameAs = null)
        if (current is Attempt.Success) return opened(current, restored = false)

        val previous = attempt(
            store.readPrevious(),
            unlocker,
            // A rejected credential would be rejected again by an identical header; don't pay
            // for a second Argon2 run.
            skipUnlockIfSameAs = (current as? Attempt.Rejected)?.header,
        )
        if (previous is Attempt.Success) {
            // C18: re-save immediately so vault.bin is good again and vault.prev matches it.
            val repaired = save(previous.header, previous.vault, previous.key, sensitive = true)
            return opened(previous.copy(header = repaired), restored = true)
        }
        return when {
            current is Attempt.Rejected || previous is Attempt.Rejected -> OpenResult.Rejected
            else -> OpenResult.Unreadable(
                listOf(current, previous).filterIsInstance<Attempt.TooNew>().maxOfOrNull { it.version },
            )
        }
    }

    /** First save of a new vault (setup). [header] must have generation 1. */
    fun create(header: VaultHeader, vault: Vault, key: AeadKey) {
        require(header.generation == 1L) { "a new vault starts at generation 1" }
        check(!store.hasAnyVault()) { "a vault already exists" }
        store.write(VaultFormat.seal(header, vault, key))
    }

    /**
     * Saves [vault] with the next generation and returns the header actually written.
     * [sensitive] = PIN change, recovery reset, biometric removal, entry delete or clear
     * history: `vault.prev` is then overwritten too, so no stale secret survives (C18).
     */
    fun save(header: VaultHeader, vault: Vault, key: AeadKey, sensitive: Boolean = false): VaultHeader {
        val next = header.copy(generation = header.generation + 1)
        store.write(VaultFormat.seal(next, vault, key))
        if (sensitive) store.replacePreviousWithCurrent()
        return next
    }

    private fun opened(success: Attempt.Success, restored: Boolean) =
        OpenResult.Opened(success.vault, success.header, success.key, restored)

    private sealed interface Attempt {
        data class Success(val vault: Vault, val header: VaultHeader, val key: AeadKey) : Attempt
        data class Rejected(val header: VaultHeader) : Attempt
        data class TooNew(val version: Int) : Attempt
        data object Unusable : Attempt
    }

    private fun attempt(file: ByteArray?, unlocker: Unlocker, skipUnlockIfSameAs: VaultHeader?): Attempt {
        file ?: return Attempt.Unusable
        val sealed = try {
            VaultFormat.parse(file)
        } catch (e: UnsupportedVaultVersionException) {
            return Attempt.TooNew(e.version)
        } catch (_: CorruptVaultException) {
            return Attempt.Unusable
        }
        if (skipUnlockIfSameAs != null && sameCredentials(sealed.header, skipUnlockIfSameAs)) {
            return Attempt.Rejected(sealed.header)
        }
        val dek = try {
            unlocker.unlock(sealed.header)
        } catch (_: CorruptVaultException) {
            return Attempt.Unusable // wrapped key modified: try the other file
        } ?: return Attempt.Rejected(sealed.header)
        val key = AeadKey.takeOwnership(dek)
        return try {
            Attempt.Success(VaultFormat.open(sealed, key), sealed.header, key)
        } catch (_: DecryptionFailedException) {
            key.close()
            Attempt.Unusable
        } catch (_: CorruptVaultException) {
            key.close()
            Attempt.Unusable
        }
    }

    private fun sameCredentials(a: VaultHeader, b: VaultHeader) =
        a.copy(generation = 0) == b.copy(generation = 0)
}
