package io.github.mnvkalyansambhana.offgridvault

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import javax.crypto.Cipher

/**
 * Class-3 biometric prompt bound to a Keystore operation (S2, M6). Platform API, not
 * androidx.biometric (T20). The [Cipher] only becomes usable once the prompt succeeds.
 */
object Biometrics {

    /** A strong (class-3) biometric is present and enrolled. Weak face unlock does not count. */
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** [onResult] gets the authenticated cipher, or `null` if the user cancelled or it failed. */
    fun authenticate(activity: Activity, title: String, description: String, cipher: Cipher, onResult: (Cipher?) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setDescription(description)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ -> onResult(null) }
            .build()
        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(result.cryptoObject?.cipher)
                // A non-matching finger keeps the prompt open; only errors/cancel end it.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(null)
            },
        )
    }
}
