package io.github.mnvkalyansambhana.offgridvault.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals data with a key that cannot leave this device. Implemented by [DeviceKey] (Android
 * Keystore); tests use a software stand-in.
 */
interface DeviceSealer {
    /** @return `iv ‖ ciphertext ‖ tag`. */
    fun seal(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /**
     * @throws DecryptionFailedException if the blob or associated data was modified.
     * @throws DeviceKeyUnavailableException if the device key is gone or permanently invalid.
     */
    fun open(sealed: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * K_device (C3, SECURITY_DESIGN §2): AES-256-GCM in Android Keystore, StrongBox when available
 * (TEE otherwise), no user authentication. It binds the PIN and recovery copies of the DEK to
 * this phone, so a copied vault file is useless elsewhere and PIN guessing must run here.
 *
 * `setUnlockedDeviceRequired` stays off until the S21 device tests prove the key survives
 * changing and removing the screen lock (losing K_device would make the vault unrecoverable).
 */
class DeviceKey(
    private val alias: String = ALIAS,
    private val unlockedDeviceRequired: Boolean = false,
) : DeviceSealer {

    /** Creates the key if needed. Call once at setup; later calls are no-ops. */
    fun ensureExists() {
        if (load() == null) generate()
    }

    fun exists(): Boolean = load() != null

    /**
     * Whether the key lives in the StrongBox secure element (vs the TEE). For diagnostics only;
     * `null` on Android 11, which has no public API to tell the two apart.
     */
    fun isStrongBoxBacked(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val key = load() ?: return false
        val info = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
    }

    fun delete() {
        keyStore().deleteEntry(alias)
    }

    override fun seal(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val key = load() ?: throw DeviceKeyUnavailableException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key) // Keystore picks a fresh random IV.
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw DeviceKeyUnavailableException()
        }
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV size" }
        return iv + cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray, associatedData: ByteArray): ByteArray {
        if (sealed.size < IV_BYTES + TAG_BYTES) throw DecryptionFailedException()
        val key = load() ?: throw DeviceKeyUnavailableException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, sealed, 0, IV_BYTES))
            cipher.updateAAD(associatedData)
            return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw DeviceKeyUnavailableException()
        } catch (_: AEADBadTagException) {
            throw DecryptionFailedException()
        } catch (_: GeneralSecurityException) {
            throw DecryptionFailedException()
        }
    }

    private fun load(): SecretKey? = try {
        keyStore().getKey(alias, null) as? SecretKey
    } catch (_: UnrecoverableKeyException) {
        null
    }

    private fun generate() {
        try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .setUnlockedDeviceRequired(unlockedDeviceRequired)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    companion object {
        const val ALIAS = "offgridvault.k_device.v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}

/** K_device is missing or permanently invalidated: this vault can no longer be opened here. */
class DeviceKeyUnavailableException : CryptoException("Device key unavailable")
