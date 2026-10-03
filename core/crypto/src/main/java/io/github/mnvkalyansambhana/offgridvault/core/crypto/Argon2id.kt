package io.github.mnvkalyansambhana.offgridvault.core.crypto

/**
 * Argon2id parameters (C14). Memory and iterations are stored in the vault header; parallelism
 * is always 1 because libsodium's `crypto_pwhash` does not support anything else.
 *
 * The constructor enforces the C14 floor, so a tampered or buggy header can never make the KDF
 * weaker than the libsodium INTERACTIVE preset.
 */
data class Argon2Params(
    val iterations: Int,
    val memoryKiB: Int = MEMORY_KIB,
) {
    init {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations out of range: $iterations" }
        require(memoryKiB in MEMORY_KIB..MAX_MEMORY_KIB) { "memoryKiB out of range: $memoryKiB" }
    }

    val memoryBytes: Long get() = memoryKiB.toLong() * 1024

    companion object {
        /** 64 MiB (C14). */
        const val MEMORY_KIB = 64 * 1024
        const val MIN_ITERATIONS = 2
        const val MAX_ITERATIONS = 32
        const val MAX_MEMORY_KIB = 1024 * 1024
    }
}

/**
 * Low-level password-hash call, so the KDF logic can run on the libsodium build for Android
 * ([SodiumArgon2Engine]) and for the JVM in unit tests. Implementations write [out] and return
 * `false` on failure (typically: not enough memory).
 */
fun interface Argon2Engine {
    fun hash(out: ByteArray, password: ByteArray, salt: ByteArray, iterations: Long, memoryBytes: Long): Boolean
}

/** Argon2id v1.3 key derivation (C1, C13): PIN → 256-bit KEK_pin. */
class Argon2id(private val engine: Argon2Engine) {

    /**
     * Derives a [KEY_BYTES]-byte key. The caller owns [password] and should wipe it; the
     * returned key must be wiped by the caller after use.
     */
    fun deriveKey(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray {
        require(salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes" }
        val out = ByteArray(KEY_BYTES)
        val ok = engine.hash(out, password, salt, params.iterations.toLong(), params.memoryBytes)
        if (!ok) {
            out.wipe()
            throw CryptoException("Argon2id failed (out of memory?)")
        }
        return out
    }

    companion object {
        const val KEY_BYTES = 32
        const val SALT_BYTES = 16

        fun newSalt(): ByteArray = Randomness.bytes(SALT_BYTES)
    }
}

/** Crypto failure without secret-bearing details. */
open class CryptoException(message: String) : Exception(message)
