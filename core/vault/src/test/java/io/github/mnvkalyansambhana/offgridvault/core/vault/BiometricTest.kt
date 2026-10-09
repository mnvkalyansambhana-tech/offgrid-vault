package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate.UnlockResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * M6: fingerprint copy of the DEK (S2, S3, S12, C16, C18). K_bio is a Keystore key bound to a
 * biometric prompt, so here a plain AES-GCM key stands in for "K_bio after the prompt".
 */
class BiometricTest {

    @get:Rule val tmp = TemporaryFolder()

    private val keys = VaultKeys(fastArgon2, FakeDevice())
    private val dir: File get() = tmp.root
    private val repo get() = VaultRepository(VaultStore(dir))
    private val attempts get() = PinAttempts(File(dir, "pin.attempts"))
    private val gate get() = PinGate(repo, keys, attempts)
    private val enrollment get() = BiometricEnrollment(repo)
    private val kBio = AesGcm.newKey()
    private val pin = "483920".toByteArray()
    private val wrong = "905317".toByteArray()

    private fun setUpWithFingerprint(): VaultRepository.OpenResult.Opened {
        val created = VaultSetup(repo, keys) {}.create(pin, Bip39.newEntropy(), Argon2Params(iterations = 3))
        val dek = (gate.verify(created.header, pin) as PinGate.VerifyResult.Correct).dek
        val sealed = AesGcm.encrypt(kBio, dek, VaultKeys.BIO_LABEL)
        val header = enrollment.enable(created.header, created.vault, created.key, sealed)
        assertTrue(keys.hasBiometric(header))
        return created
    }

    /** What the app does after a successful prompt: K_bio opens the stored copy. */
    private fun dekFromFingerprint(): ByteArray =
        AesGcm.decrypt(kBio, repo.peekHeader()!!.wrapped_key_bio.toByteArray(), VaultKeys.BIO_LABEL)

    @Test
    fun fingerprintUnlocks_andResetsTheWrongPinCounter() {
        setUpWithFingerprint()
        gate.unlock(wrong)
        gate.unlock(wrong)
        val ok = gate.unlockWithBiometric(dekFromFingerprint()) as UnlockResult.Unlocked
        assertEquals(2, ok.previousFailures) // S12 notice still shown
        assertEquals(0, attempts.failures())
    }

    @Test
    fun fingerprintIsBlockedAfterThreeWrongPins() {
        setUpWithFingerprint()
        repeat(3) { gate.unlock(wrong) }
        val dek = dekFromFingerprint()
        assertEquals(UnlockResult.LockedOut, gate.unlockWithBiometric(dek))
        assertTrue(dek.all { it == 0.toByte() }) // wiped even when refused
        assertTrue(gate.isLockedOut())
    }

    @Test
    fun aDekThatDoesNotOpenTheVault_isRejected_withoutTouchingTheCounter() {
        setUpWithFingerprint()
        gate.unlock(wrong)
        assertEquals(UnlockResult.BiometricRejected, gate.unlockWithBiometric(AesGcm.newKey()))
        assertEquals(1, attempts.failures())
    }

    @Test
    fun disable_removesTheCopy_fromBothSaves() {
        val created = setUpWithFingerprint()
        val opened = gate.unlock(pin) as UnlockResult.Unlocked
        val header = enrollment.disable(opened.opened.header, opened.opened.vault, opened.opened.key)
        assertFalse(keys.hasBiometric(header))
        val previous = VaultFormat.parse(File(dir, "vault.prev").readBytes())
        assertFalse(keys.hasBiometric(previous.header)) // C18 sensitive save
        assertTrue(gate.unlock(pin) is UnlockResult.Unlocked) // PIN unaffected
        created.key.close()
    }

    @Test
    fun pinChange_keepsTheFingerprintCopyWorking() {
        val created = setUpWithFingerprint()
        val reset = PinReset(repo, keys, attempts) { Argon2Params(iterations = 4) }
        val opened = gate.unlock(pin) as UnlockResult.Unlocked
        val dek = keys.unwrapWithPin(opened.opened.header, pin)!!
        val before = dekFromFingerprint()
        val header = reset.setNewPin(opened.opened.header, opened.opened.vault, opened.opened.key, dek, "271946".toByteArray())
        assertTrue(keys.hasBiometric(header))
        assertArrayEquals(before, dekFromFingerprint()) // same DEK, same copy
        assertTrue(gate.unlockWithBiometric(dekFromFingerprint()) is UnlockResult.Unlocked)
        created.key.close()
    }
}
