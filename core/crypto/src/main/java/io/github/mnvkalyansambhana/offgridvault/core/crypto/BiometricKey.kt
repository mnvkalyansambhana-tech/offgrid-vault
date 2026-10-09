package io.github.mnvkalyansambhana.offgridvault.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * K_bio (S2, C3, SECURITY_DESIGN §2): AES-256-GCM in Android Keystore that can only be used right
 * after a **class-3 (strong) biometric** check, once per operation (timeout 0), and is
 * **permanently invalidated when a new fingerprint is enrolled**. It wraps the optional biometric
 * copy of the DEK.
 *
 * Every use is two steps: [sealer]/[opener] return an [Operation] whose [Operation.cipher] goes
 * into `BiometricPrompt.CryptoObject`; only after the prompt succeeds may the operation finish.
 *
 * `setUnlockedDeviceRequired` stays off, like K_device, until the S21 device tests.
 */
class BiometricKey(private val alias: String = ALIAS) {

    /** Replaces any existing K_bio with a fresh one. Needs at least one enrolled strong biometric. */
    fun create() {
        delete()
        try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    fun exists(): Boolean = load() != null

    fun delete() {
        keyStore().deleteEntry(alias)
    }

    /** @throws BiometricKeyInvalidatedException if the key is gone or a new fingerprint was added. */
    fun sealer(): Operation {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        init { cipher.init(Cipher.ENCRYPT_MODE, it) } // Keystore picks a fresh random IV.
        return Operation(cipher, sealed = null)
    }

    /**
     * @param sealed `iv ‖ ciphertext ‖ tag` from an earlier [Operation.seal].
     * @throws BiometricKeyInvalidatedException if the key is gone or a new fingerprint was added.
     * @throws DecryptionFailedException if [sealed] is malformed.
     */
    fun opener(sealed: ByteArray): Operation {
        if (sealed.size < IV_BYTES + TAG_BYTES) throw DecryptionFailedException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        init { cipher.init(Cipher.DECRYPT_MODE, it, GCMParameterSpec(TAG_BYTES * 8, sealed, 0, IV_BYTES)) }
        return Operation(cipher, sealed)
    }

    /** One authenticated use of K_bio. Call exactly one of [seal]/[open], after the prompt succeeds. */
    class Operation internal constructor(val cipher: Cipher, private val sealed: ByteArray?) {

        /** @return `iv ‖ ciphertext ‖ tag`. */
        fun seal(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
            check(sealed == null) { "opener cannot seal" }
            cipher.updateAAD(associatedData)
            val iv = cipher.iv
            check(iv.size == IV_BYTES) { "unexpected IV size" }
            return try {
                iv + cipher.doFinal(plaintext)
            } catch (_: GeneralSecurityException) {
                throw BiometricKeyInvalidatedException()
            }
        }

        /** @throws DecryptionFailedException if the blob or associated data was modified. */
        fun open(associatedData: ByteArray): ByteArray {
            val blob = checkNotNull(sealed) { "sealer cannot open" }
            cipher.updateAAD(associatedData)
            return try {
                cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
            } catch (_: GeneralSecurityException) {
                throw DecryptionFailedException()
            }
        }
    }

    /** Keystore's view of K_bio, for on-device tests only (no secret material). */
    internal fun keyInfo(): KeyInfo? {
        val key = load() ?: return null
        return SecretKeyFactory.getInstance(key.algorithm, KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
    }

    private inline fun init(block: (SecretKey) -> Unit) {
        val key = load() ?: throw BiometricKeyInvalidatedException()
        try {
            block(key)
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException()
        } catch (_: InvalidKeyException) {
            // Some Keystore versions report an invalidated key as a plain InvalidKeyException.
            throw BiometricKeyInvalidatedException()
        }
    }

    private fun load(): SecretKey? = try {
        keyStore().getKey(alias, null) as? SecretKey
    } catch (_: UnrecoverableKeyException) {
        null
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            // C12: one code path — every use needs a fresh class-3 biometric check.
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setUnlockedDeviceRequired(false) // S21: pending device tests
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    companion object {
        const val ALIAS = "offgridvault.k_bio.v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}

/** K_bio is missing or was invalidated by a new fingerprint: fingerprint unlock must be set up again. */
class BiometricKeyInvalidatedException : CryptoException("Biometric key invalidated")
