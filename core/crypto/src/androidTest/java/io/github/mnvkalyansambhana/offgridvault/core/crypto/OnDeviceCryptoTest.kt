package io.github.mnvkalyansambhana.offgridvault.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a real phone: proves the *Android* libsodium/JNA binaries load and agree with the
 * vectors used in the JVM tests. `./gradlew :core:crypto:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceCryptoTest {

    @Test
    fun androidLibsodium_matchesKnownArgon2idVector() {
        val key = AndroidSodium.argon2id.deriveKey(
            password = "123456".toByteArray(),
            salt = ByteArray(16) { it.toByte() },
            params = Argon2Params(iterations = 2),
        )
        assertEquals("e4184a32e48201d4fd44de348b232660c8f5c36aef3c2cd421dc11e1a3bf03a4", key.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun aesGcm_roundTripsOnDevice() {
        val key = AesGcm.newKey()
        val plaintext = "pin-wrapped dek".toByteArray()
        assertArrayEquals(plaintext, AesGcm.decrypt(key, AesGcm.encrypt(key, plaintext, byteArrayOf(1)), byteArrayOf(1)))
    }

    @Test
    fun bip39Wordlist_loadsFromApkResources() {
        assertEquals("legal winner thank year wave sausage worth useful legal winner thank yellow",
            Bip39.toWords(ByteArray(16) { 0x7f }).joinToString(" "))
    }
}
