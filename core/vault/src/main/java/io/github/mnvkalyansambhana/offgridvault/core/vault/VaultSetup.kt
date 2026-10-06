package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.RecoveryKdf
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Creates a new vault at the very end of setup (P17): random DEK, PIN copy (Argon2id with
 * on-device [params], C14) and recovery copy (HKDF with per-vault salt, C19), both bound to
 * K_device; empty payload; generation 1.
 */
class VaultSetup(
    private val repository: VaultRepository,
    private val keys: VaultKeys,
    private val prepareDeviceKey: () -> Unit,
) {

    /**
     * [recoveryEntropy] `null` = recovery words skipped for now (P19, S28). Wipes neither [pin]
     * nor [recoveryEntropy]; the caller owns them.
     */
    fun create(pin: ByteArray, recoveryEntropy: ByteArray?, params: Argon2Params): VaultRepository.OpenResult.Opened {
        prepareDeviceKey()
        val dek = AesGcm.newKey()
        try {
            val pinSalt = Argon2id.newSalt()
            val recoverySalt = RecoveryKdf.newSalt()
            val header = VaultHeader(
                generation = 1,
                argon2_iterations = params.iterations,
                argon2_memory_kib = params.memoryKiB,
                pin_salt = pinSalt.toByteString(),
                recovery_salt = recoverySalt.toByteString(),
                wrapped_key_pin = keys.wrapWithPin(dek, pin, pinSalt, params).toByteString(),
                wrapped_key_recovery = recoveryEntropy
                    ?.let { keys.wrapWithRecovery(dek, it, recoverySalt).toByteString() }
                    ?: ByteString.EMPTY,
            )
            val vault = Vault()
            val key = AeadKey.takeOwnership(dek.copyOf())
            repository.create(header, vault, key)
            return VaultRepository.OpenResult.Opened(vault, header, key, restoredFromPrevious = false)
        } finally {
            dek.wipe()
        }
    }
}

/**
 * "Set up recovery words later" (P19, S28): adds the recovery copy to an existing vault. The
 * caller must have re-verified the PIN and passes the DEK it unwrapped; words can be added
 * only once.
 */
class RecoveryEnrollment(private val repository: VaultRepository, private val keys: VaultKeys) {

    fun enroll(
        header: VaultHeader,
        vault: Vault,
        key: AeadKey,
        dek: ByteArray,
        recoveryEntropy: ByteArray,
    ): VaultHeader {
        check(!keys.hasRecovery(header)) { "recovery words already set up (never regenerated, S28)" }
        val wrapped = keys.wrapWithRecovery(dek, recoveryEntropy, header.recovery_salt.toByteArray())
        return repository.save(header.copy(wrapped_key_recovery = wrapped.toByteString()), vault, key)
    }
}
