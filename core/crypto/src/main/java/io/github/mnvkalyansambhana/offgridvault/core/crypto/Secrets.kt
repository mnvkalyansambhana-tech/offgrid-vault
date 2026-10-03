package io.github.mnvkalyansambhana.offgridvault.core.crypto

import java.security.SecureRandom

/**
 * Overwrites a secret in place. Best effort only (S25): the JVM may already have copied the
 * array (e.g. during GC compaction or inside a JCA provider), so this shortens the exposure
 * window rather than guaranteeing erasure.
 */
fun ByteArray.wipe() = fill(0)

/** See [ByteArray.wipe]. */
fun CharArray.wipe() = fill('\u0000')

/** Runs [block] with this secret and wipes it afterwards, even if [block] throws. */
inline fun <T> ByteArray.useAndWipe(block: (ByteArray) -> T): T =
    try {
        block(this)
    } finally {
        wipe()
    }

/**
 * The only source of randomness for keys, salts, nonces and recovery words (S5, C2).
 * `SecureRandom()` uses the platform CSPRNG (/dev/urandom-backed on Android); never seeded.
 */
object Randomness {
    private val rng = SecureRandom()

    fun bytes(count: Int): ByteArray {
        require(count > 0) { "count must be positive" }
        return ByteArray(count).also(rng::nextBytes)
    }

    /** Uniform in `0 until bound` (rejection sampling inside SecureRandom — no modulo bias). */
    fun int(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return rng.nextInt(bound)
    }
}
