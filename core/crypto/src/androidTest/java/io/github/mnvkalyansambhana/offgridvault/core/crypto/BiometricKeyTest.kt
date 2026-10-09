package io.github.mnvkalyansambhana.offgridvault.core.crypto

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.os.Build
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M6: K_bio on the real Keystore. No finger is touched here, so these prove the **binding**: the
 * key is hardware-backed, needs a class-3 biometric for every use, dies with a new enrolment, and
 * cannot seal/open anything without the prompt. Invalidation by adding a fingerprint is a manual
 * test (DEV_SETUP M6 #5). Skipped on devices without an enrolled strong biometric.
 */
@RunWith(AndroidJUnit4::class)
class BiometricKeyTest {

    private val key = BiometricKey(alias = "offgridvault.test.k_bio")
    private val ad = "offgrid-vault/v1/wrap/bio".toByteArray()

    @Before
    fun needsStrongBiometric() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val status = context.getSystemService(BiometricManager::class.java).canAuthenticate(BIOMETRIC_STRONG)
        assumeTrue("enrol a fingerprint on the test device", status == BiometricManager.BIOMETRIC_SUCCESS)
    }

    @After
    fun cleanUp() = key.delete()

    @Test
    fun keyIsHardwareBacked_biometricPerUse_andInvalidatedByEnrolment() {
        key.create()
        val info = checkNotNull(key.keyInfo())
        assertTrue(info.isUserAuthenticationRequired)
        assertTrue(info.isInvalidatedByBiometricEnrollment)
        assertEquals(0, info.userAuthenticationValidityDurationSeconds) // every use needs the prompt
        assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, info.userAuthenticationType)
        assertEquals(256, info.keySize)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // StrongBox when available, TEE otherwise — never software.
            assertNotEquals(KeyProperties.SECURITY_LEVEL_SOFTWARE, info.securityLevel)
            assertNotEquals(KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE, info.securityLevel)
        } else {
            @Suppress("DEPRECATION")
            assertTrue(info.isInsideSecureHardware)
        }
    }

    @Test
    fun cannotSealWithoutTheBiometricPrompt() {
        key.create()
        val operation = key.sealer() // preparing is fine: this is what goes into the prompt
        assertThrows(Exception::class.java) { operation.seal(AesGcm.newKey(), ad) }
    }

    @Test
    fun cannotOpenWithoutTheBiometricPrompt() {
        key.create()
        val fakeCopy = ByteArray(12 + 32 + 16) { it.toByte() }
        assertThrows(Exception::class.java) { key.opener(fakeCopy).open(ad) }
    }

    @Test
    fun malformedCopy_isRejectedBeforeThePrompt() {
        key.create()
        assertThrows(DecryptionFailedException::class.java) { key.opener(ByteArray(10)) }
    }

    @Test
    fun deletedKey_isReportedAsInvalidated() {
        key.create()
        assertTrue(key.exists())
        key.delete()
        assertFalse(key.exists())
        assertThrows(BiometricKeyInvalidatedException::class.java) { key.sealer() }
        assertThrows(BiometricKeyInvalidatedException::class.java) { key.opener(ByteArray(60)) }
    }
}
