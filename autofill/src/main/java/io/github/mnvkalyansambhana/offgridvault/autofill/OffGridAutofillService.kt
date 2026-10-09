package io.github.mnvkalyansambhana.offgridvault.autofill

import android.content.ComponentName
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultSession

/** Implemented by the Application: the one process-wide session the UI also uses (T3/T21). */
interface AutofillHost {
    val autofillSession: VaultSession
    /** The app's `AutofillActivity` (unlock, search, save). */
    val autofillActivity: ComponentName
}

/**
 * OffGrid Vault's autofill service (M7, S13–S16). Offline by construction: matching uses only
 * the bundled PSL/browser list and the signing certs reported by PackageManager.
 */
class OffGridAutofillService : AutofillService() {

    private val matcher by lazy { Matcher() }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val host = application as? AutofillHost ?: return callback.onSuccess(null)
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val parsed = ParsedRequest.parse(structure) ?: return callback.onSuccess(null)
        if (parsed.packageName == packageName) return callback.onSuccess(null) // never fill ourselves
        val responses = FillResponses(this, request.inlineSuggestionsRequest, host.autofillActivity)
        val requester = matcher.identify(parsed.packageName, SigningCerts.current(packageManager, parsed.packageName), parsed.webDomain)
        val unlocked = host.autofillSession.state.value as? VaultSession.State.Unlocked
        if (unlocked == null) {
            // S16: a locked vault reveals nothing — not even whether anything matches.
            callback.onSuccess(responses.locked(parsed, canSave = requester != null))
            return
        }
        callback.onSuccess(responses.unlocked(parsed, requester, unlocked.content, matcher))
    }

    /**
     * P11 (M8): the user tapped "Save" in Android's save UI. Nothing is written here: the typed
     * login is parked in-process and our save screen (unlock first if needed) lets the user
     * review it — new entry, or a password update that keeps the old one in history (C15).
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val host = application as? AutofillHost ?: return callback.onSuccess()
        val structures = request.fillContexts.map { it.structure }
        val parsed = structures.lastOrNull()?.let(ParsedRequest::parse) ?: return callback.onSuccess()
        if (parsed.packageName == packageName) return callback.onSuccess()
        val requester = matcher.identify(parsed.packageName, SigningCerts.current(packageManager, parsed.packageName), parsed.webDomain)
            ?: return callback.onSuccess() // S32: unknown identity → never saved
        // Username may sit on an earlier screen of the same login flow (two-step sign-in).
        val usernameIds = structures.asReversed().mapNotNull { s -> ParsedRequest.parse(s)?.fields?.username }
        val typed = ParsedRequest.typedText(structures, (usernameIds + listOfNotNull(parsed.fields.password)).toSet())
        val password = parsed.fields.password?.let(typed::get)?.takeIf { it.isNotEmpty() } ?: return callback.onSuccess()
        val username = usernameIds.firstNotNullOfOrNull(typed::get) ?: ""
        val label = when (requester) {
            is Requester.Web -> requester.host
            is Requester.App -> runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(requester.packageName, 0)).toString()
            }.getOrDefault(requester.packageName)
        }
        val token = PendingSaves.put(PendingSave(requester, label, username.trim(), password.toByteArray(Charsets.UTF_8)))
        callback.onSuccess(FillResponses(this, null, host.autofillActivity).saveSender(token))
    }
}
