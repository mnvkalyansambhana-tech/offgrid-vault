package io.github.mnvkalyansambhana.offgridvault.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmTest {

    private val key = AesGcm.newKey()
    private val aad = "header-v1".toByteArray()

    @Test
    fun decryptsGcmSpecTestCase16() {
        // McGrew & Viega GCM spec, Test Case 16 (AES-256, 96-bit IV, with AAD); verified with OpenSSL.
        val k = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".hex()
        val iv = "cafebabefacedbaddecaf888".hex()
        val p = ("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39").hex()
        val a = "feedfacedeadbeeffeedfacedeadbeefabaddad2".hex()
        val c = ("522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
            "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662").hex()
        val t = "76fc6ece0f4e1768cddf8853bb2d551b".hex()
        assertArrayEquals(p, AesGcm.decrypt(k, iv + c + t, a))
    }

    @Test
    fun roundTrip_andLayout() {
        val plaintext = "correct horse battery staple".toByteArray()
        val sealed = AesGcm.encrypt(key, plaintext, aad)
        assertEquals(plaintext.size + AesGcm.OVERHEAD_BYTES, sealed.size)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, sealed, aad))
    }

    @Test
    fun emptyPlaintext_roundTrips() {
        assertArrayEquals(ByteArray(0), AesGcm.decrypt(key, AesGcm.encrypt(key, ByteArray(0), aad), aad))
    }

    @Test
    fun anyModification_isRejected() {
        val sealed = AesGcm.encrypt(key, "secret".toByteArray(), aad)
        val positions = listOf(0, AesGcm.NONCE_BYTES, sealed.size - 1) // nonce, ciphertext, tag
        positions.forEach { i ->
            val tampered = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows(DecryptionFailedException::class.java) { AesGcm.decrypt(key, tampered, aad) }
        }
        assertThrows(DecryptionFailedException::class.java) { AesGcm.decrypt(key, sealed, "header-v2".toByteArray()) }
        assertThrows(DecryptionFailedException::class.java) { AesGcm.decrypt(AesGcm.newKey(), sealed, aad) }
        assertThrows(DecryptionFailedException::class.java) { AesGcm.decrypt(key, sealed.copyOf(10), aad) }
    }

    @Test
    fun everyEncryption_usesAFreshNonce() {
        val nonces = (1..10_000).map { AesGcm.encrypt(key, byteArrayOf(1), aad).copyOf(AesGcm.NONCE_BYTES).toHex() }
        assertEquals(nonces.size, nonces.toSet().size)
    }

    @Test
    fun rejectsNon256BitKeys() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.encrypt(ByteArray(16), byteArrayOf(1), aad) }
    }
}
