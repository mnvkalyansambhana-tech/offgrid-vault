package io.github.mnvkalyansambhana.offgridvault.core.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.aead.AesGcmKey
import com.google.crypto.tink.aead.AesGcmParameters
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.util.SecretBytes
import java.security.GeneralSecurityException

/**
 * AES-256-GCM for software keys — the DEK and Argon2/HKDF-derived KEKs (C2, T8). Keystore
 * keys (K_device, K_bio) use JCA directly instead.
 *
 * Tink picks a fresh random 96-bit nonce for every encryption, so callers cannot reuse one.
 * Output layout (Tink NO_PREFIX): `nonce(12) ‖ ciphertext ‖ tag(16)`.
 */
object AesGcm {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    const val OVERHEAD_BYTES = NONCE_BYTES + TAG_BYTES

    private val parameters: AesGcmParameters = AesGcmParameters.builder()
        .setKeySizeBytes(KEY_BYTES)
        .setIvSizeBytes(NONCE_BYTES)
        .setTagSizeBytes(TAG_BYTES)
        .setVariant(AesGcmParameters.Variant.NO_PREFIX)
        .build()

    fun newKey(): ByteArray = Randomness.bytes(KEY_BYTES)

    /** [associatedData] is authenticated but not encrypted (e.g. the vault header, C6). */
    fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead(key).encrypt(plaintext, associatedData)

    /**
     * @throws DecryptionFailedException if the key is wrong or anything (nonce, ciphertext, tag,
     * associated data) was modified. Deliberately carries no detail.
     */
    fun decrypt(key: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size < OVERHEAD_BYTES) throw DecryptionFailedException()
        return try {
            aead(key).decrypt(ciphertext, associatedData)
        } catch (_: GeneralSecurityException) {
            throw DecryptionFailedException()
        }
    }

    private fun aead(key: ByteArray): Aead {
        require(key.size == KEY_BYTES) { "AES-256-GCM needs a $KEY_BYTES-byte key" }
        val tinkKey = AesGcmKey.builder()
            .setParameters(parameters)
            .setKeyBytes(SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get()))
            .build()
        return AesGcmJce.create(tinkKey)
    }
}

class DecryptionFailedException : CryptoException("Decryption failed")
