package io.github.mnvkalyansambhana.offgridvault.autofill

import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryDraft
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultContent
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.LinkedApp
import okio.ByteString.Companion.decodeHex

/** What saving a typed login will do (P11). Pure: unit-tested. */
sealed interface SavePlan {
    /** Same login, new password: the old one moves to history (C15). */
    data class Update(val entry: EntryView) : SavePlan
    /** Already saved with this exact password: nothing to do. */
    data class Unchanged(val entry: EntryView) : SavePlan
    /**
     * A new entry, linked per S13/S14. [savedAccounts] = the other logins already saved for this
     * app/site (P23): the user may pick one to update instead, but it is never assumed.
     */
    data class New(val savedAccounts: List<EntryView> = emptyList()) : SavePlan

    companion object {
        /**
         * P23: one entry per account. Only an entry for this requester (S13/S14) **with the same,
         * non-empty username** counts as the same login; anything else is a new account.
         */
        fun of(content: VaultContent, matcher: Matcher, save: PendingSave, password: ByteArray): SavePlan {
            val matches = matcher.matches(content.entries, save.requester)
            val target = matches.firstOrNull { save.username.isNotEmpty() && it.username.equals(save.username, ignoreCase = true) }
                ?: return New(matches)
            return forEntry(content, target, password)
        }

        /** The user chose to update [entry] (or it has the same username). */
        fun forEntry(content: VaultContent, entry: EntryView, password: ByteArray): SavePlan =
            if (content.hasPassword(entry.id, password)) Unchanged(entry) else Update(entry)

        fun apply(content: VaultContent, plan: SavePlan, save: PendingSave, password: ByteArray, nowMillis: Long): VaultContent =
            when (plan) {
                is Unchanged -> content
                is Update -> content.updatePassword(plan.entry.id, password, nowMillis)
                is New -> when (val r = save.requester) {
                    // S14: only an allowlisted browser's domain becomes a website.
                    is Requester.Web -> content.upsert(EntryDraft(null, r.domainKey, save.username, listOf(r.host), password, ByteArray(0)), nowMillis)
                    is Requester.App -> {
                        val added = content.upsert(EntryDraft(null, save.label, save.username, emptyList(), password, ByteArray(0)), nowMillis)
                        // S13: pinned to the cert this app is signed with right now.
                        val id = added.entries.last().id
                        added.linkApp(id, LinkedApp(r.packageName, r.certs.min().decodeHex()), nowMillis)
                    }
                }
            }
    }
}
