package io.github.mnvkalyansambhana.offgridvault.core.crypto

import com.google.crypto.tink.subtle.Hkdf

/** HKDF-SHA256 (RFC 5869) via Tink (T8). */
object HkdfSha256 {
    /** An empty [salt] means "no salt" (RFC 5869: a hash-length string of zeros). */
    fun derive(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..MAX_LENGTH) { "length out of range" }
        return Hkdf.computeHkdf("HMACSHA256", inputKeyMaterial, salt, info, length)
    }

    private const val MAX_LENGTH = 255 * 32
}

/**
 * Recovery words → KEK_rec (S5, C3): HKDF-SHA256 over the 128-bit BIP-39 entropy. The words
 * already carry full entropy, so no slow KDF is needed. The per-vault [vaultSalt] and the
 * versioned label keep this key distinct from anything else ever derived from the same words.
 */
object RecoveryKdf {
    const val SALT_BYTES = 16
    private val INFO = "offgrid-vault/v1/recovery-kek".toByteArray(Charsets.US_ASCII)

    fun deriveKek(entropy: ByteArray, vaultSalt: ByteArray): ByteArray {
        require(entropy.size == Bip39.ENTROPY_BYTES) { "recovery entropy must be ${Bip39.ENTROPY_BYTES} bytes" }
        require(vaultSalt.size == SALT_BYTES) { "vault salt must be $SALT_BYTES bytes" }
        return HkdfSha256.derive(entropy, vaultSalt, INFO, AesGcm.KEY_BYTES)
    }

    fun newSalt(): ByteArray = Randomness.bytes(SALT_BYTES)
}
