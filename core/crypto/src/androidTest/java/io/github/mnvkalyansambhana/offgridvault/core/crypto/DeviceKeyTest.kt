package io.github.mnvkalyansambhana.offgridvault.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Keystore (TEE/StrongBox). Uses a test alias, never the app's K_device. */
@RunWith(AndroidJUnit4::class)
class DeviceKeyTest {

    private val key = DeviceKey(alias = "offgridvault.test.k_device")
    private val ad = "offgrid-vault/v1/wrap/pin".toByteArray()

    @After
    fun cleanUp() = key.delete()

    @Test
    fun sealOpen_roundTrips() {
        key.ensureExists()
        val secret = AesGcm.newKey()
        assertArrayEquals(secret, key.open(key.seal(secret, ad), ad))
    }

    @Test
    fun everySeal_usesAFreshIv() {
        key.ensureExists()
        val a = key.seal(byteArrayOf(1), ad)
        val b = key.seal(byteArrayOf(1), ad)
        assertFalse(a.copyOf(12).contentEquals(b.copyOf(12)))
    }

    @Test
    fun tamperingOrWrongLabel_isDetected() {
        key.ensureExists()
        val sealed = key.seal(byteArrayOf(9, 9, 9), ad)
        val flipped = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(DecryptionFailedException::class.java) { key.open(flipped, ad) }
        assertThrows(DecryptionFailedException::class.java) { key.open(sealed, "other".toByteArray()) }
    }

    @Test
    fun missingKey_isUnavailable() {
        key.ensureExists()
        val sealed = key.seal(byteArrayOf(1), ad)
        key.delete()
        assertFalse(key.exists())
        assertThrows(DeviceKeyUnavailableException::class.java) { key.open(sealed, ad) }
    }

    @Test
    fun ensureExists_isIdempotent() {
        key.ensureExists()
        val sealed = key.seal(byteArrayOf(7), ad)
        key.ensureExists() // must not replace the key
        assertTrue(key.open(sealed, ad).contentEquals(byteArrayOf(7)))
    }
}
