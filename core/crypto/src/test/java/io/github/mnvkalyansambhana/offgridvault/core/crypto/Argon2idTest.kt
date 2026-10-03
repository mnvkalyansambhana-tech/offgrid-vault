package io.github.mnvkalyansambhana.offgridvault.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * libsodium's crypto_pwhash cannot take RFC 9106's secret/associated data or parallelism 4, so
 * the RFC vector validates an independent oracle (Bouncy Castle), which then cross-checks
 * libsodium at our real parameters (p = 1, 64 MiB).
 */
class Argon2idTest {

    @Test
    fun oracle_matchesRfc9106Argon2idVector() {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(3)
            .withMemoryAsKB(32)
            .withParallelism(4)
            .withSalt(ByteArray(16) { 0x02 })
            .withSecret(ByteArray(8) { 0x03 })
            .withAdditional(ByteArray(12) { 0x04 })
            .build()
        val tag = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(ByteArray(32) { 0x01 }, tag)
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.toHex())
    }

    @Test
    fun libsodium_matchesKnownVectorAtProductionParameters() {
        // Expected value computed independently with OpenSSL (Python `cryptography` Argon2id).
        val key = jvmSodiumArgon2.deriveKey(
            password = "123456".toByteArray(),
            salt = ByteArray(16) { it.toByte() },
            params = Argon2Params(iterations = 2),
        )
        assertEquals("e4184a32e48201d4fd44de348b232660c8f5c36aef3c2cd421dc11e1a3bf03a4", key.toHex())
    }

    @Test
    fun libsodium_matchesOracleOnRandomInputs() {
        repeat(2) { round ->
            val password = Randomness.bytes(6 + round)
            val salt = Argon2id.newSalt()
            val params = Argon2Params(iterations = 2 + round)
            assertArrayEquals(oracle(password, salt, params), jvmSodiumArgon2.deriveKey(password, salt, params))
        }
    }

    @Test
    fun differentSaltOrPin_givesDifferentKey() {
        val params = Argon2Params(iterations = 2)
        val salt = Argon2id.newSalt()
        val base = jvmSodiumArgon2.deriveKey("123456".toByteArray(), salt, params)
        assertFalse(base.contentEquals(jvmSodiumArgon2.deriveKey("123457".toByteArray(), salt, params)))
        assertFalse(base.contentEquals(jvmSodiumArgon2.deriveKey("123456".toByteArray(), Argon2id.newSalt(), params)))
    }

    @Test
    fun params_enforceC14Floor() {
        assertThrows(IllegalArgumentException::class.java) { Argon2Params(iterations = 1) }
        assertThrows(IllegalArgumentException::class.java) { Argon2Params(iterations = 2, memoryKiB = 32 * 1024) }
        assertEquals(64L * 1024 * 1024, Argon2Params(iterations = 2).memoryBytes)
    }

    @Test
    fun rejectsWrongSaltLength() {
        assertThrows(IllegalArgumentException::class.java) {
            jvmSodiumArgon2.deriveKey("1".toByteArray(), ByteArray(8), Argon2Params(2))
        }
    }

    @Test
    fun engineFailure_throwsWithoutReturningKey() {
        val failing = Argon2id { _, _, _, _, _ -> false }
        assertThrows(CryptoException::class.java) {
            failing.deriveKey("1".toByteArray(), Argon2id.newSalt(), Argon2Params(2))
        }
    }

    private fun oracle(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val bcParams = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKiB)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        return ByteArray(Argon2id.KEY_BYTES).also {
            Argon2BytesGenerator().apply { init(bcParams) }.generateBytes(password, it)
        }
    }
}
