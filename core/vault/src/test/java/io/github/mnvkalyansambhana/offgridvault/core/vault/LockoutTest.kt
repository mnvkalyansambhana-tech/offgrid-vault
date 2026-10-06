package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Params
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate.UnlockResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate.VerifyResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinReset.RecoverResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** M4: attempt counter, 3-strike lockout, recovery → new PIN, PIN change, inactivity. */
class LockoutTest {

    @get:Rule val tmp = TemporaryFolder()

    private val kdf = CountingFastArgon2()
    private val device = FakeDevice()
    private val keys = VaultKeys(kdf.argon2, device)
    private val dir: File get() = tmp.root
    private val repo get() = VaultRepository(VaultStore(dir))
    private val counterFile get() = File(dir, "pin.attempts")
    private val attempts get() = PinAttempts(counterFile)
    private val gate get() = PinGate(repo, keys, attempts)
    private val reset get() = PinReset(repo, keys, attempts) { Argon2Params(iterations = 4) }
    private val pin = "483920".toByteArray()
    private val wrong = "905317".toByteArray()
    private val entropy = Bip39.newEntropy()

    private fun setUp(withRecovery: Boolean = true) =
        VaultSetup(repo, keys) {}.create(pin, if (withRecovery) entropy else null, Argon2Params(iterations = 3))

    // --- PinAttempts (S17, S29) ---

    @Test
    fun counter_missingIsZero_garbageFailsClosed() {
        assertEquals(0, attempts.failures())
        counterFile.writeText("not a number")
        assertEquals(PinAttempts.MAX_FAILURES, attempts.failures())
        assertTrue(attempts.isLockedOut())
        counterFile.writeText("-4")
        assertTrue(attempts.isLockedOut())
    }

    @Test
    fun counter_isPersistedBeforeTheCheck_soKillingTheAppCannotSkipIt() {
        // Simulate the process dying right after recordAttempt (before any verification result).
        val before = attempts.recordAttempt()
        assertEquals(0, before)
        assertEquals(1, PinAttempts(counterFile).failures()) // a fresh instance sees it on disk
    }

    @Test
    fun counter_crashWhileWritingKeepsTheOldValue() {
        attempts.recordAttempt()
        val crashing = object : VaultStore.FileOps by VaultStore.FileOps.Real {
            override fun moveAtomically(from: File, to: File) = throw IOException("power loss")
        }
        runCatching { PinAttempts(counterFile, crashing).recordAttempt() }
        assertEquals(1, attempts.failures()) // old value intact, never a torn file
    }

    // --- PinGate (S3, S12) ---

    @Test
    fun threeWrongPins_lockOut_andNoFurtherArgon2Runs() {
        setUp()
        assertEquals(UnlockResult.Wrong(2), gate.unlock(wrong))
        assertEquals(UnlockResult.Wrong(1), gate.unlock(wrong))
        assertEquals(UnlockResult.LockedOut, gate.unlock(wrong))
        val callsAtLockout = kdf.calls
        assertEquals(UnlockResult.LockedOut, gate.unlock(pin)) // even the right PIN
        assertEquals(callsAtLockout, kdf.calls)
    }

    @Test
    fun success_resetsCounter_andReportsPreviousFailures() {
        setUp()
        gate.unlock(wrong)
        gate.unlock(wrong)
        val ok = gate.unlock(pin) as UnlockResult.Unlocked
        assertEquals(2, ok.previousFailures)
        assertEquals(0, attempts.failures())
        assertEquals(UnlockResult.Wrong(2), gate.unlock(wrong)) // fresh budget
    }

    @Test
    fun deviceKeyLoss_isNotCountedAsAWrongPin() {
        setUp()
        device.gone = true
        assertEquals(UnlockResult.DeviceKeyLost, gate.unlock(pin))
        assertEquals(0, attempts.failures())
    }

    @Test
    fun verify_sharesTheSameBudget_andLocksOut() {
        val header = setUp().header
        gate.unlock(wrong)
        assertEquals(VerifyResult.Wrong(1), gate.verify(header, wrong))
        assertEquals(VerifyResult.LockedOut, gate.verify(header, wrong))
        assertTrue(gate.isLockedOut())
        assertEquals(UnlockResult.LockedOut, gate.unlock(pin))
    }

    // --- PinReset (S3, S6, S24, C18) ---

    @Test
    fun recoveryWords_unlockAfterLockout_andNewPinWorks_oldPinDoesNot() {
        setUp()
        repeat(3) { gate.unlock(wrong) }
        assertTrue(gate.isLockedOut())

        val recovered = reset.recover(Bip39.toWords(entropy)) as RecoverResult.Recovered
        val newPin = "271946".toByteArray()
        val header = reset.setNewPin(recovered.opened.header, recovered.opened.vault, recovered.opened.key, recovered.dek, newPin)
        assertEquals(4, header.argon2_iterations) // freshly calibrated
        assertFalse(gate.isLockedOut())

        assertTrue(gate.unlock("271946".toByteArray()) is UnlockResult.Unlocked)
        assertEquals(UnlockResult.Wrong(2), gate.unlock(pin)) // the old PIN is dead
    }

    @Test
    fun afterNewPin_previousSaveCannotBeOpenedWithOldPin() {
        val created = setUp()
        val dek = keys.unwrapWithPin(created.header, pin)!!
        reset.setNewPin(created.header, created.vault, created.key, dek, "271946".toByteArray())
        val prev = VaultFormat.parse(File(dir, "vault.prev").readBytes())
        assertNull(keys.unwrapWithPin(prev.header, pin)) // C18 sensitive save
    }

    @Test
    fun wrongWords_or_noWords() {
        setUp()
        assertEquals(RecoverResult.WrongWords, reset.recover(Bip39.toWords(Bip39.newEntropy())))
        assertEquals(true, reset.hasRecoveryWords())
    }

    @Test
    fun vaultWithoutRecoveryWords_staysLocked() {
        setUp(withRecovery = false)
        assertEquals(false, reset.hasRecoveryWords())
        assertEquals(RecoverResult.NoRecoveryWords, reset.recover(Bip39.toWords(entropy)))
    }

    @Test
    fun recoveryDoesNotTouchTheCounterUntilANewPinIsSet() {
        setUp()
        repeat(3) { gate.unlock(wrong) }
        assertTrue(reset.recover(Bip39.toWords(entropy)) is RecoverResult.Recovered)
        assertTrue(gate.isLockedOut()) // abandoning here leaves it locked
    }

    // --- Erase & start over (S30) ---

    @Test
    fun erase_removesEverything_andAllowsAFreshSetup() {
        setUp()
        repeat(3) { gate.unlock(wrong) }
        var deviceKeyDeleted = false
        val session = VaultSession()
        VaultEraser(repo, attempts, session) { deviceKeyDeleted = true }.eraseEverything()

        assertTrue(deviceKeyDeleted)
        assertFalse(repo.hasVault())
        assertFalse(File(dir, "vault.bin").exists() || File(dir, "vault.prev").exists())
        assertEquals(0, attempts.failures())
        assertTrue(session.state.value is VaultSession.State.Locked)
        // Fresh setup works and has a clean attempt budget.
        setUp()
        assertTrue(gate.unlock(pin) is UnlockResult.Unlocked)
    }

    @Test
    fun eraseConfirmation_isExact() {
        assertTrue(VaultEraser.isConfirmed("ERASE"))
        assertTrue(VaultEraser.isConfirmed("  ERASE "))
        assertFalse(VaultEraser.isConfirmed("erase"))
        assertFalse(VaultEraser.isConfirmed("ERASE!"))
        assertFalse(VaultEraser.isConfirmed(""))
    }

    // --- SessionTimeout (S23) ---

    @Test
    fun inactivityTimeout() {
        var now = 0L
        val timeout = SessionTimeout(timeoutMillis = 300_000) { now }
        now = 299_999
        assertFalse(timeout.isExpired())
        timeout.touch()
        now += 299_999
        assertFalse(timeout.isExpired())
        now += 1
        assertTrue(timeout.isExpired())
    }

    @Test
    fun openResultStillWorksForPlainUnlock() {
        setUp()
        assertTrue(repo.open { keys.unwrapWithPin(it, pin) } is OpenResult.Opened)
    }
}
