package io.github.mnvkalyansambhana.offgridvault

import android.app.assist.AssistStructure
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.autofill.AutofillManager
import android.view.inputmethod.InlineSuggestionsRequest
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import io.github.mnvkalyansambhana.offgridvault.autofill.FillResponses
import io.github.mnvkalyansambhana.offgridvault.autofill.Matcher
import io.github.mnvkalyansambhana.offgridvault.autofill.ParsedRequest
import io.github.mnvkalyansambhana.offgridvault.autofill.SigningCerts
import io.github.mnvkalyansambhana.offgridvault.ui.autofill.AutofillFlow
import io.github.mnvkalyansambhana.offgridvault.ui.autofill.SaveLoginFlow
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridTheme

/**
 * The second activity allowed by T21: autofill unlock ("Unlock OffGrid Vault"), "Search vault…"
 * and save (M8). Not exported, own task, excluded from recents, FLAG_SECURE like MainActivity.
 * Everything it needs comes from the system's `EXTRA_ASSIST_STRUCTURE`, never from the requester.
 */
class AutofillActivity : ComponentActivity() {

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.sessionGuard.touch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(this)
        val mode = intent.getStringExtra(FillResponses.EXTRA_MODE)
        if (mode == FillResponses.MODE_OPEN_APP) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        if (mode == FillResponses.MODE_SAVE) {
            val token = intent.getStringExtra(FillResponses.EXTRA_SAVE_TOKEN)
            if (token == null) {
                finish()
                return
            }
            showContent {
                SaveLoginFlow(container, token, onDone = ::finish, onOpenApp = ::openApp)
            }
            return
        }
        val parsed = extra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)?.let(ParsedRequest::parse)
        if (parsed == null || parsed.packageName == packageName || mode == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val responses = FillResponses(
            this,
            extra<InlineSuggestionsRequest>(AutofillManager.EXTRA_INLINE_SUGGESTIONS_REQUEST),
            ComponentName(this, AutofillActivity::class.java),
        )
        val matcher = Matcher()
        val requester = matcher.identify(parsed.packageName, SigningCerts.current(packageManager, parsed.packageName), parsed.webDomain)

        showContent {
            AutofillFlow(
                app = container,
                mode = mode,
                request = parsed,
                requester = requester,
                requesterLabel = appLabel(parsed.packageName),
                responses = responses,
                matcher = matcher,
                onResult = { result ->
                    setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result))
                    finish()
                },
                onCancel = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
                onOpenApp = ::openApp,
            )
        }
    }

    private fun showContent(content: @Composable () -> Unit) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent { OffGridTheme { content() } }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private inline fun <reified T : Parcelable> extra(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(name)
        }
}
