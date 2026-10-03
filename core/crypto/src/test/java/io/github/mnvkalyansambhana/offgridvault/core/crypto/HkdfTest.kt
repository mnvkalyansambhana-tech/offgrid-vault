package io.github.mnvkalyansambhana.offgridvault.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class HkdfTest {

    @Test
    fun rfc5869TestCase1() {
        val okm = HkdfSha256.derive(
            inputKeyMaterial = ByteArray(22) { 0x0b },
            salt = "000102030405060708090a0b0c".hex(),
            info = "f0f1f2f3f4f5f6f7f8f9".hex(),
            length = 42,
        )
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm.toHex())
    }

    @Test
    fun rfc5869TestCase3_emptySaltAndInfo() {
        val okm = HkdfSha256.derive(ByteArray(22) { 0x0b }, ByteArray(0), ByteArray(0), 42)
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8", okm.toHex())
    }

    @Test
    fun recoveryKek_isDeterministicAndSaltBound() {
        val entropy = Bip39.newEntropy()
        val salt = RecoveryKdf.newSalt()
        val kek = RecoveryKdf.deriveKek(entropy, salt)
        assertEquals(AesGcm.KEY_BYTES, kek.size)
        assertEquals(kek.toHex(), RecoveryKdf.deriveKek(entropy, salt).toHex())
        assertFalse(kek.contentEquals(RecoveryKdf.deriveKek(entropy, RecoveryKdf.newSalt())))
    }

    @Test
    fun recoveryKek_rejectsWrongSizes() {
        assertThrows(IllegalArgumentException::class.java) { RecoveryKdf.deriveKek(ByteArray(32), RecoveryKdf.newSalt()) }
        assertThrows(IllegalArgumentException::class.java) { RecoveryKdf.deriveKek(Bip39.newEntropy(), ByteArray(8)) }
    }
}
