package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DecryptionFailedException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceSealer
import io.github.mnvkalyansambhana.offgridvault.core.crypto.RecoveryKdf
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader

/**
 * Wraps and unwraps the DEK (C3, SECURITY_DESIGN §2). Each copy is double-wrapped:
 *
 * ```
 * inner = AES-GCM(KEK, DEK, ad = label ‖ salt)     KEK = Argon2id(PIN) or HKDF(recovery entropy)
 * outer = K_device.seal(inner, ad = label)         K_device never leaves the phone
 * ```
 * The outer layer makes a copied vault file useless on any other device; the inner layer means
 * K_device alone (e.g. via root) still needs the PIN, at Argon2 cost per guess.
 *
 * Failures are told apart: a wrong PIN/words fails the inner layer (`null`), a modified blob fails
 * the outer layer ([CorruptVaultException]), a missing device key throws
 * [io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKeyUnavailableException].
 */
class VaultKeys(private val argon2: Argon2id, private val device: DeviceSealer) {

    fun wrapWithPin(dek: ByteArray, pin: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray =
        wrap(dek, argon2.deriveKey(pin, salt, params), PIN_LABEL, salt)

    /** @return the DEK, or `null` if the PIN is wrong. */
    fun unwrapWithPin(header: VaultHeader, pin: ByteArray): ByteArray? {
        val salt = header.pin_salt.toByteArray()
        val inner = openOuter(header.wrapped_key_pin.toByteArray(), PIN_LABEL)
        val params = Argon2Params(header.argon2_iterations, header.argon2_memory_kib)
        return unwrapInner(inner, argon2.deriveKey(pin, salt, params), PIN_LABEL, salt)
    }

    fun wrapWithRecovery(dek: ByteArray, entropy: ByteArray, salt: ByteArray): ByteArray =
        wrap(dek, RecoveryKdf.deriveKek(entropy, salt), RECOVERY_LABEL, salt)

    /** @return the DEK, or `null` if the words don't match this vault or none were set up. */
    fun unwrapWithRecovery(header: VaultHeader, entropy: ByteArray): ByteArray? {
        if (!hasRecovery(header)) return null
        val salt = header.recovery_salt.toByteArray()
        val inner = openOuter(header.wrapped_key_recovery.toByteArray(), RECOVERY_LABEL)
        return unwrapInner(inner, RecoveryKdf.deriveKek(entropy, salt), RECOVERY_LABEL, salt)
    }

    /** Whether recovery words were set up for this vault (S28). */
    fun hasRecovery(header: VaultHeader): Boolean = header.wrapped_key_recovery.size > 0

    private fun wrap(dek: ByteArray, kek: ByteArray, label: ByteArray, salt: ByteArray): ByteArray {
        val inner = try {
            AesGcm.encrypt(kek, dek, label + salt)
        } finally {
            kek.wipe()
        }
        return device.seal(inner, label)
    }

    private fun openOuter(blob: ByteArray, label: ByteArray): ByteArray = try {
        device.open(blob, label)
    } catch (_: DecryptionFailedException) {
        throw CorruptVaultException("wrapped key modified")
    }

    private fun unwrapInner(inner: ByteArray, kek: ByteArray, label: ByteArray, salt: ByteArray): ByteArray? =
        try {
            AesGcm.decrypt(kek, inner, label + salt)
        } catch (_: DecryptionFailedException) {
            null
        } finally {
            kek.wipe()
            inner.wipe()
        }

    private companion object {
        val PIN_LABEL = "offgrid-vault/v1/wrap/pin".toByteArray(Charsets.US_ASCII)
        val RECOVERY_LABEL = "offgrid-vault/v1/wrap/recovery".toByteArray(Charsets.US_ASCII)
    }
}
