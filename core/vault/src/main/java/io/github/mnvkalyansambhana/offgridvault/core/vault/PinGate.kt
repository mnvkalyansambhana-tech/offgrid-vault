package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKeyUnavailableException
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository.OpenResult
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.VaultHeader

/**
 * The only way a PIN is checked (S3, S12, S17, S24, S28): unlock, change PIN and "set up recovery
 * words later" all count toward the same 3-strike limit. Once locked out, no PIN is even tried —
 * only recovery words ([PinReset]) unlock.
 */
class PinGate(
    private val repository: VaultRepository,
    private val keys: VaultKeys,
    private val attempts: PinAttempts,
) {

    sealed interface UnlockResult {
        /** [previousFailures] > 0 → show "N wrong PIN attempts since your last unlock" (S12). */
        class Unlocked(val opened: OpenResult.Opened, val previousFailures: Int) : UnlockResult
        data class Wrong(val attemptsLeft: Int) : UnlockResult
        data object LockedOut : UnlockResult
        data object NoVault : UnlockResult
        data object Unreadable : UnlockResult
        data object DeviceKeyLost : UnlockResult
    }

    sealed interface VerifyResult {
        /** The caller owns and must wipe [dek]. */
        class Correct(val dek: ByteArray) : VerifyResult
        data class Wrong(val attemptsLeft: Int) : VerifyResult
        /** Third strike while the vault was open: the caller must lock the session now. */
        data object LockedOut : VerifyResult
    }

    fun isLockedOut(): Boolean = attempts.isLockedOut()

    fun unlock(pin: ByteArray): UnlockResult {
        if (!repository.hasVault()) return UnlockResult.NoVault
        if (attempts.isLockedOut()) return UnlockResult.LockedOut
        val before = attempts.recordAttempt()
        val result = try {
            repository.open { header -> keys.unwrapWithPin(header, pin) }
        } catch (_: DeviceKeyUnavailableException) {
            attempts.restore(before) // not a wrong PIN
            return UnlockResult.DeviceKeyLost
        }
        return when (result) {
            is OpenResult.Opened -> {
                attempts.reset()
                UnlockResult.Unlocked(result, previousFailures = before)
            }
            OpenResult.Rejected -> wrongOrLockedOut(before + 1, UnlockResult::Wrong, UnlockResult.LockedOut)
            OpenResult.NoVault -> {
                attempts.restore(before)
                UnlockResult.NoVault
            }
            is OpenResult.Unreadable -> {
                attempts.restore(before)
                UnlockResult.Unreadable
            }
        }
    }

    /** Re-checks the PIN of the open vault (change PIN S24, recovery words later S28). */
    fun verify(header: VaultHeader, pin: ByteArray): VerifyResult {
        if (attempts.isLockedOut()) return VerifyResult.LockedOut
        val before = attempts.recordAttempt()
        val dek = keys.unwrapWithPin(header, pin)
            ?: return wrongOrLockedOut(before + 1, VerifyResult::Wrong, VerifyResult.LockedOut)
        attempts.reset()
        return VerifyResult.Correct(dek)
    }

    private fun <T> wrongOrLockedOut(failures: Int, wrong: (Int) -> T, lockedOut: T): T =
        if (failures >= PinAttempts.MAX_FAILURES) lockedOut else wrong(PinAttempts.MAX_FAILURES - failures)
}
