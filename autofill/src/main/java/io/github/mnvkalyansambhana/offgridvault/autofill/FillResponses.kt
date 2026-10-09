package io.github.mnvkalyansambhana.offgridvault.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultContent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds what the keyboard strip / dropdown shows (S16): locked → only "Unlock OffGrid Vault";
 * unlocked → the matching logins then "Search vault…"; never a non-matching entry. Passwords are
 * decrypted only for matching entries, right when the response is built (S9).
 */
@SuppressLint("RestrictedApi")
class FillResponses(
    private val context: Context,
    private val inlineRequest: InlineSuggestionsRequest?,
    private val authActivity: ComponentName,
) {

    /** [canSave]: offer P11 save for this requester (known identity, a password field). */
    fun locked(request: ParsedRequest, canSave: Boolean): FillResponse {
        @Suppress("DEPRECATION") // Field-based builders are API 33+
        val builder = FillResponse.Builder()
            .setAuthentication(request.ids, authSender(MODE_UNLOCK), presentation(UNLOCK_TITLE, null), inline(0, UNLOCK_TITLE, null))
        if (canSave) saveInfo(request)?.let(builder::setSaveInfo)
        return builder.build()
    }

    fun unlocked(request: ParsedRequest, requester: Requester?, content: VaultContent, matcher: Matcher): FillResponse {
        val matches = requester?.let { matcher.matches(content.entries, it) }.orEmpty()
        val builder = FillResponse.Builder()
        // S32: an app we can't identify is never saved for.
        if (requester != null) saveInfo(request)?.let(builder::setSaveInfo)
        val shown = matches.take(MAX_MATCHES)
        shown.forEachIndexed { i, entry -> builder.addDataset(filled(request, entry, content, i)) }
        builder.addDataset(search(request, shown.size))
        return builder.build()
    }

    /** The dataset that actually fills [entry] (also the result of "Search vault…"). */
    @Suppress("DEPRECATION")
    fun filled(request: ParsedRequest, entry: EntryView, content: VaultContent, index: Int = 0): Dataset {
        val title = entry.title.ifEmpty { "(untitled)" }
        val subtitle = entry.username.ifEmpty { null }
        val dataset = Dataset.Builder(presentation(title, subtitle))
        inline(index, title, subtitle)?.let(dataset::setInlinePresentation)
        request.fields.username?.let { id -> if (entry.username.isNotEmpty()) dataset.setValue(id, AutofillValue.forText(entry.username)) }
        request.fields.password?.let { id ->
            val plain = content.reveal(entry.password)
            try {
                dataset.setValue(id, AutofillValue.forText(String(plain, Charsets.UTF_8)))
            } finally {
                plain.wipe()
            }
        }
        if (request.fields.password == null && entry.username.isEmpty()) {
            // Nothing to put in the only field; Dataset needs at least one value.
            request.fields.username?.let { dataset.setValue(it, AutofillValue.forText("")) }
        }
        return dataset.build()
    }

    /**
     * P11: ask Android to offer "Save to OffGrid Vault" once a password was typed. The user
     * confirms in the system UI, then reviews (and unlocks) in our save screen.
     */
    private fun saveInfo(request: ParsedRequest): SaveInfo? {
        val password = request.fields.password ?: return null
        val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or
            (if (request.fields.username != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
        return SaveInfo.Builder(type, arrayOf(password))
            .apply { request.fields.username?.let { setOptionalIds(arrayOf(it)) } }
            .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            .build()
    }

    /** P11: opens the save screen; the token refers to an in-process [PendingSave]. */
    fun saveSender(token: String): IntentSender {
        val intent = Intent().setComponent(authActivity).putExtra(EXTRA_MODE, MODE_SAVE).putExtra(EXTRA_SAVE_TOKEN, token)
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCodes.incrementAndGet(), intent, flags).intentSender
    }

    @Suppress("DEPRECATION")
    private fun search(request: ParsedRequest, index: Int): Dataset {
        val dataset = Dataset.Builder(presentation(SEARCH_TITLE, null))
        inline(index, SEARCH_TITLE, null)?.let(dataset::setInlinePresentation)
        request.ids.forEach { dataset.setValue(it, null) }
        return dataset.setAuthentication(authSender(MODE_SEARCH)).build()
    }

    private fun presentation(title: String, subtitle: String?) =
        RemoteViews(context.packageName, R.layout.offgrid_autofill_item).apply {
            setTextViewText(R.id.offgrid_autofill_title, title)
            setTextViewText(R.id.offgrid_autofill_subtitle, subtitle.orEmpty())
        }

    private fun inline(index: Int, title: String, subtitle: String?): InlinePresentation? {
        val request = inlineRequest ?: return null
        val specs = request.inlinePresentationSpecs
        if (specs.isEmpty() || index >= request.maxSuggestionCount) return null
        val spec = specs[minOf(index, specs.size - 1)]
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
        val content = InlineSuggestionUi.newContentBuilder(attribution())
            .setTitle(title)
            .setContentDescription(if (subtitle != null) "$title, $subtitle" else title)
        subtitle?.let(content::setSubtitle)
        return InlinePresentation(content.build().slice, spec, false)
    }

    /** Long-press on an inline chip opens the app (required attribution). */
    private fun attribution(): PendingIntent {
        val launch = Intent().setComponent(authActivity).putExtra(EXTRA_MODE, MODE_OPEN_APP)
        return PendingIntent.getActivity(context, ATTRIBUTION_REQUEST, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Explicit component, so a mutable PendingIntent is safe; it must be mutable for the system to
     * add `EXTRA_ASSIST_STRUCTURE` when it launches the activity.
     */
    private fun authSender(mode: String): IntentSender {
        val intent = Intent().setComponent(authActivity).putExtra(EXTRA_MODE, mode)
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getActivity(context, requestCodes.incrementAndGet(), intent, flags).intentSender
    }

    companion object {
        const val EXTRA_MODE = "io.github.mnvkalyansambhana.offgridvault.autofill.MODE"
        const val MODE_UNLOCK = "unlock"
        const val MODE_SEARCH = "search"
        const val MODE_OPEN_APP = "open-app"
        const val MODE_SAVE = "save"
        const val EXTRA_SAVE_TOKEN = "io.github.mnvkalyansambhana.offgridvault.autofill.SAVE_TOKEN"
        const val UNLOCK_TITLE = "Unlock OffGrid Vault"
        const val SEARCH_TITLE = "Search vault…"
        private const val MAX_MATCHES = 5
        private const val ATTRIBUTION_REQUEST = 1
        private val requestCodes = AtomicInteger(1000)
    }
}
