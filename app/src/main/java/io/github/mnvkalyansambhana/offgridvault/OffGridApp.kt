package io.github.mnvkalyansambhana.offgridvault

import android.app.Application
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.view.autofill.AutofillManager
import io.github.mnvkalyansambhana.offgridvault.autofill.AutofillHost
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AndroidSodium
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Calibrator
import io.github.mnvkalyansambhana.offgridvault.core.crypto.BiometricKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKey
import io.github.mnvkalyansambhana.offgridvault.core.vault.BiometricEnrollment
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinAttempts
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinGate
import io.github.mnvkalyansambhana.offgridvault.core.vault.PinReset
import io.github.mnvkalyansambhana.offgridvault.core.vault.RecoveryEnrollment
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultEraser
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultKeys
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultRepository
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSetup
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultStore
import java.io.File

class OffGridApp : Application(), AutofillHost {
    lateinit var container: AppContainer
        private set

    // M7: the autofill service shares the app's one VaultSession (T3/T21).
    override val autofillSession: VaultSession get() = container.session
    override val autofillActivity: ComponentName get() = ComponentName(this, AutofillActivity::class.java)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency injection (T5): every long-lived object, created once per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    /** The vault lives in no-backup, credential-encrypted app storage. */
    private val vaultDir = File(context.noBackupFilesDir, "vault")
    val repository = VaultRepository(VaultStore(vaultDir))
    val session = VaultSession()
    private val attempts = PinAttempts(File(vaultDir, "pin.attempts"))
    val deviceKey = DeviceKey()
    /** K_bio (S2, M6): exists only while fingerprint unlock is on. */
    val biometricKey = BiometricKey()
    val biometricEnrollment by lazy { BiometricEnrollment(repository) }
    val keys by lazy { VaultKeys(AndroidSodium.argon2id, deviceKey) }
    val calibrator by lazy { Argon2Calibrator(AndroidSodium.argon2id) }
    val setup by lazy { VaultSetup(repository, keys) { deviceKey.ensureExists() } }
    val enrollment by lazy { RecoveryEnrollment(repository, keys) }
    val gate by lazy { PinGate(repository, keys, attempts) }
    val eraser by lazy { VaultEraser(repository, attempts, session) {
            deviceKey.delete()
            biometricKey.delete()
        } }
    val pinReset by lazy { PinReset(repository, keys, attempts) { calibrator.calibrate().params } }

    /** S23: locks on screen-off and after 5 minutes without interaction. */
    val sessionGuard = SessionGuard(context, session)

    /** S11/S18: sensitive clips, cleared after 30 s or on lock. */
    val clipboard = SecureClipboard(context, session)

    /** S22: a PIN, pattern or password screen lock is required. */
    fun isDeviceSecure(): Boolean = keyguard.isDeviceSecure

    /** M7: OffGrid Vault is the phone's selected autofill service. */
    fun isAutofillService(): Boolean =
        appContext.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true

    /** M6: a class-3 biometric is enrolled, so fingerprint unlock can be offered. */
    fun hasStrongBiometric(): Boolean = Biometrics.isAvailable(appContext)

    /** M6: removes the fingerprint copy (sensitive save, C18), then K_bio. No PIN needed to remove a way in. */
    fun turnOffFingerprint() {
        val unlocked = session.state.value as? VaultSession.State.Unlocked ?: return
        if (keys.hasBiometric(unlocked.header)) {
            session.updated(biometricEnrollment.disable(unlocked.header, unlocked.content.toVault(), unlocked.key))
        }
        biometricKey.delete()
    }

    /**
     * M6: the vault still lists a fingerprint copy but K_bio is gone (a new fingerprint was added,
     * or the copy stopped working). Removes the dead copy with a sensitive save (C18).
     */
    fun dropStaleFingerprint() {
        val unlocked = session.state.value as? VaultSession.State.Unlocked ?: return
        if (!keys.hasBiometric(unlocked.header) || biometricKey.exists()) return
        session.updated(biometricEnrollment.disable(unlocked.header, unlocked.content.toVault(), unlocked.key))
    }
}

val Context.container: AppContainer get() = (applicationContext as OffGridApp).container
