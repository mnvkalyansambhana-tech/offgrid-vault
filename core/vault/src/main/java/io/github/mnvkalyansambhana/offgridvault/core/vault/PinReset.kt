package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKeyUnavailableException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import okio.ByteString.Companion.toByteString

/**
 * Forgotten PIN → recovery words → new PIN (S3, S6, S24, S28), and the shared "set a new PIN"
 * step used by Change PIN. A new PIN gets a new salt and a fresh Argon2 calibration (C14), and is
 * saved as a **sensitive** save so `vault.prev` can't be opened with the old PIN (C18).
 */
class PinReset(
    private val repository: VaultRepository,
    private val keys: VaultKeys,
    private val attempts: PinAttempts,
    private val calibrate: () -> Argon2Params,
) {

    sealed interface RecoverResult {
        /** The caller owns and must wipe [dek] after [setNewPin]. */
        class Recovered(val opened: OpenResult.Opened, val dek: ByteArray) : RecoverResult
        data object WrongWords : RecoverResult
        /** Recovery words were never set up: a locked-out vault stays locked (S6). */
        data object NoRecoveryWords : RecoverResult
        data object Unreadable : RecoverResult
        data object DeviceKeyLost : RecoverResult
    }

    /** Whether this vault has recovery words, read from the (unauthenticated) header — UI only. */
    fun hasRecoveryWords(): Boolean? = repository.peekHeader()?.let(keys::hasRecovery)

    /** @throws io.github.mnvkalyansambhana.offgridvault.core.crypto.InvalidMnemonicException for malformed words. */
    fun recover(words: List<String>): RecoverResult {
        val entropy = Bip39.toEntropy(words)
        var dek: ByteArray? = null
        var noRecovery = false
        val result = try {
            repository.open { header ->
                if (!keys.hasRecovery(header)) {
                    noRecovery = true
                    null
                } else {
                    keys.unwrapWithRecovery(header, entropy)?.also { dek = it.copyOf() }
                }
            }
        } catch (_: DeviceKeyUnavailableException) {
            return RecoverResult.DeviceKeyLost
        } finally {
            entropy.wipe()
        }
        return when (result) {
            is OpenResult.Opened -> RecoverResult.Recovered(result, checkNotNull(dek))
            OpenResult.Rejected -> if (noRecovery) RecoverResult.NoRecoveryWords else RecoverResult.WrongWords
            OpenResult.NoVault, is OpenResult.Unreadable -> RecoverResult.Unreadable
        }
    }

    /** Re-wraps the PIN copy with [newPin]; returns the header actually saved. Resets the counter. */
    fun setNewPin(header: VaultHeader, vault: Vault, key: AeadKey, dek: ByteArray, newPin: ByteArray): VaultHeader {
        val params = calibrate()
        val salt = Argon2id.newSalt()
        val wrapped = keys.wrapWithPin(dek, newPin, salt, params)
        val next = header.copy(
            argon2_iterations = params.iterations,
            argon2_memory_kib = params.memoryKiB,
            pin_salt = salt.toByteString(),
            wrapped_key_pin = wrapped.toByteString(),
        )
        val saved = repository.save(next, vault, key, sensitive = true)
        attempts.reset()
        return saved
    }
}
