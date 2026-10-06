package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Engine
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2id
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKeyUnavailableException
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceSealer
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** Setup → unlock with PIN / recovery words, with a software stand-in for K_device. */
class SetupUnlockTest {

    @get:Rule val tmp = TemporaryFolder()

    private val device = FakeDevice()
    private val dir: File get() = tmp.root
    private val repo get() = VaultRepository(VaultStore(dir))
    private val keys = VaultKeys(fastArgon2, device)
    private val pin = "483920".toByteArray()
    private val entropy = Bip39.newEntropy()
    private val params = Argon2Params(iterations = 3)

    private fun setUp(): OpenResult.Opened {
        var prepared = false
        val opened = VaultSetup(repo, keys) { prepared = true }.create(pin, entropy, params)
        assertTrue(prepared)
        return opened
    }

    private fun unlockWithPin(p: ByteArray) = repo.open { keys.unwrapWithPin(it, p) }

    @Test
    fun setupCreatesGenerationOneVault_withCalibratedParams() {
        val opened = setUp()
        assertEquals(1L, opened.header.generation)
        assertEquals(3, opened.header.argon2_iterations)
        assertEquals(0, opened.vault.entries.size)
        assertTrue(repo.hasVault())
    }

    @Test
    fun correctPin_unlocks() {
        setUp()
        assertTrue(unlockWithPin("483920".toByteArray()) is OpenResult.Opened)
    }

    @Test
    fun wrongPin_isRejected() {
        setUp()
        assertEquals(OpenResult.Rejected, unlockWithPin("483921".toByteArray()))
    }

    @Test
    fun recoveryWords_unlockTheSameVault() {
        val created = setUp()
        val words = Bip39.toWords(entropy)
        val opened = repo.open { keys.unwrapWithRecovery(it, Bip39.toEntropy(words)) } as OpenResult.Opened
        assertEquals(created.header, opened.header)
        assertEquals(OpenResult.Rejected, repo.open { keys.unwrapWithRecovery(it, Bip39.newEntropy()) })
    }

    @Test
    fun pinAndRecoveryCopies_yieldTheSameDek() {
        val header = setUp().header
        assertArrayEquals(keys.unwrapWithPin(header, pin), keys.unwrapWithRecovery(header, entropy))
    }

    @Test
    fun anotherDevice_cannotOpenTheFile() {
        val header = setUp().header
        // Same file, same PIN, different K_device (vault copied to another phone).
        val otherPhone = VaultKeys(fastArgon2, FakeDevice())
        assertThrows(CorruptVaultException::class.java) { otherPhone.unwrapWithPin(header, pin) }
    }

    @Test
    fun deviceKeyGone_isReportedNotRejected() {
        setUp()
        device.gone = true
        assertThrows(DeviceKeyUnavailableException::class.java) { unlockWithPin(pin) }
    }

    @Test
    fun tamperedWrappedKey_fallsBackToPrevious() {
        val opened = setUp()
        repo.save(opened.header, opened.vault, opened.key) // vault.prev now exists
        val bin = File(dir, "vault.bin")
        val sealed = VaultFormat.parse(bin.readBytes())
        val bad = sealed.header.wrapped_key_pin.toByteArray().also { it[5] = (it[5].toInt() xor 1).toByte() }
        // Rewrite vault.bin with a modified PIN copy (re-sealed so only the wrapped key is off).
        bin.writeBytes(VaultFormat.seal(sealed.header.copy(wrapped_key_pin = bad.toByteString()), opened.vault, opened.key))
        val reopened = unlockWithPin(pin) as OpenResult.Opened
        assertTrue(reopened.restoredFromPrevious)
    }

    @Test
    fun skippedRecovery_hasNoRecoveryCopy() {
        val opened = VaultSetup(repo, keys) {}.create(pin, recoveryEntropy = null, params = params)
        assertTrue(!keys.hasRecovery(opened.header))
        assertNull(keys.unwrapWithRecovery(opened.header, entropy))
        assertTrue(unlockWithPin(pin) is OpenResult.Opened) // PIN still works
    }

    @Test
    fun recoveryAddedLater_unlocks_andCannotBeRegenerated() {
        val opened = VaultSetup(repo, keys) {}.create(pin, recoveryEntropy = null, params = params)
        val dek = keys.unwrapWithPin(opened.header, pin)!!
        val enrollment = RecoveryEnrollment(repo, keys)
        val header = enrollment.enroll(opened.header, opened.vault, opened.key, dek, entropy)
        assertEquals(2L, header.generation)
        assertTrue(keys.hasRecovery(header))
        assertTrue(repo.open { keys.unwrapWithRecovery(it, entropy) } is OpenResult.Opened)
        assertThrows(IllegalStateException::class.java) {
            enrollment.enroll(header, opened.vault, opened.key, dek, Bip39.newEntropy())
        }
    }

    @Test
    fun unwrapWithWrongPin_returnsNull() {
        val header = setUp().header
        assertNull(keys.unwrapWithPin(header, "000001".toByteArray()))
    }
}
