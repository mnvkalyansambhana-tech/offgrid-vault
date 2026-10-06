package io.github.mnvkalyansambhana.offgridvault

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * Asks for the **phone's** screen-lock PIN/pattern/password via the system prompt (S30). Proves
 * the phone's owner is present; no key is unlocked by it. API 30+ supports device credential alone.
 */
object DeviceCredential {

    fun confirm(activity: Activity, title: String, description: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setDescription(description)
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
    }
}
