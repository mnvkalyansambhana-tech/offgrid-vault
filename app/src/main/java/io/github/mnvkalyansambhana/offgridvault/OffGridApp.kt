package io.github.mnvkalyansambhana.offgridvault

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AndroidSodium
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Argon2Calibrator
import io.github.mnvkalyansambhana.offgridvault.core.crypto.DeviceKey
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

class OffGridApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency injection (T5): every long-lived object, created once per process. */
class AppContainer(context: Context) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    /** The vault lives in no-backup, credential-encrypted app storage. */
    private val vaultDir = File(context.noBackupFilesDir, "vault")
    val repository = VaultRepository(VaultStore(vaultDir))
    val session = VaultSession()
    private val attempts = PinAttempts(File(vaultDir, "pin.attempts"))
    val deviceKey = DeviceKey()
    val keys by lazy { VaultKeys(AndroidSodium.argon2id, deviceKey) }
    val calibrator by lazy { Argon2Calibrator(AndroidSodium.argon2id) }
    val setup by lazy { VaultSetup(repository, keys) { deviceKey.ensureExists() } }
    val enrollment by lazy { RecoveryEnrollment(repository, keys) }
    val gate by lazy { PinGate(repository, keys, attempts) }
    val eraser by lazy { VaultEraser(repository, attempts, session) { deviceKey.delete() } }
    val pinReset by lazy { PinReset(repository, keys, attempts) { calibrator.calibrate().params } }

    /** S23: locks on screen-off and after 5 minutes without interaction. */
    val sessionGuard = SessionGuard(context, session)

    /** S11/S18: sensitive clips, cleared after 30 s or on lock. */
    val clipboard = SecureClipboard(context, session)

    /** S22: a PIN, pattern or password screen lock is required. */
    fun isDeviceSecure(): Boolean = keyguard.isDeviceSecure
}

val Context.container: AppContainer get() = (applicationContext as OffGridApp).container
