package io.github.mnvkalyansambhana.offgridvault.autofill

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryDraft
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultContent
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M8 exit tests (P11, C15, S13, S14). */
class SavePlanTest {

    private val matcher = Matcher()
    private val chrome = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
    private val appCert = "44".repeat(32)
    private val empty = VaultContent.from(Vault(), AeadKey.takeOwnership(AesGcm.newKey()))

    private fun web(user: String) = PendingSave(matcher.identify("com.android.chrome", setOf(chrome), "login.github.com")!!, "login.github.com", user, ByteArray(0))
    private fun app(user: String) = PendingSave(matcher.identify("com.bank.app", setOf(appCert), null)!!, "My Bank", user, ByteArray(0))
    private fun pw(s: String) = s.toByteArray()
    private fun VaultContent.pwOf(i: Int) = String(reveal(entries[i].password))

    @Test
    fun fromBrowser_newEntryWithWebsite_thenUpdateKeepsHistory() {
        val save = web("dev@example.com")
        assertEquals(SavePlan.New(), SavePlan.of(empty, matcher, save, pw("one")))
        val saved = SavePlan.apply(empty, SavePlan.New(), save, pw("one"), 1)
        assertEquals("github.com", saved.entries.single().title)
        assertEquals(listOf("login.github.com"), saved.entries.single().urls)

        val plan = SavePlan.of(saved, matcher, save, pw("two"))
        assertTrue(plan is SavePlan.Update)
        val updated = SavePlan.apply(saved, plan, save, pw("two"), 2)
        assertEquals(1, updated.entries.size)
        assertEquals("two", updated.pwOf(0))
        assertEquals("one", String(updated.reveal(updated.entries[0].history.single().password)))

        assertTrue(SavePlan.of(updated, matcher, save, pw("two")) is SavePlan.Unchanged)
    }

    @Test
    fun fromApp_newEntryIsLinkedToPackageAndCert_andMatchesNextTime() {
        val save = app("me")
        val saved = SavePlan.apply(empty, SavePlan.New(), save, pw("p"), 1)
        val e = saved.entries.single()
        assertEquals("My Bank", e.title)
        assertTrue(e.urls.isEmpty())
        assertEquals("com.bank.app", e.linkedApps.single().package_name)
        assertEquals(listOf("My Bank"), matcher.matches(saved.entries, save.requester).map { it.title })
    }

    @Test
    fun fakeBrowserClaimingADomain_isSavedAsAnApp_neverAsAWebsite() {
        val fake = PendingSave(matcher.identify("com.fake.browser", setOf("55".repeat(32)), "bank.com")!!, "Fake", "u", ByteArray(0))
        val saved = SavePlan.apply(empty, SavePlan.New(), fake, pw("p"), 1)
        assertTrue(saved.entries.single().urls.isEmpty())
    }

    @Test
    fun differentUsername_onSameSite_isANewEntry_andKeepsBothAccounts() {
        val first = SavePlan.apply(empty, SavePlan.New(), web("a@x.com"), pw("1"), 1)
        val plan = SavePlan.of(first, matcher, web("b@x.com"), pw("2"))
        assertTrue(plan is SavePlan.New)
        assertEquals(listOf("a@x.com"), (plan as SavePlan.New).savedAccounts.map { it.username }) // offered, not assumed
        val both = SavePlan.apply(first, plan, web("b@x.com"), pw("2"), 2)
        assertEquals(setOf("a@x.com", "b@x.com"), both.entries.map { it.username }.toSet())
        assertEquals(setOf("1", "2"), both.entries.map { String(both.reveal(it.password)) }.toSet())
        val unrelated = first.upsert(EntryDraft(null, "Other", "a@x.com", listOf("other.com"), pw("3"), ByteArray(0)), 2)
        assertTrue(SavePlan.of(unrelated, matcher, web("a@x.com"), pw("9")) is SavePlan.Update) // only matching entries count
    }

    @Test
    fun unknownUsername_neverOverwritesTheOnlySavedAccount() {
        // P23 regression (Instagram): username not captured, one account already saved.
        val first = SavePlan.apply(empty, SavePlan.New(), app("old_account"), pw("old"), 1)
        val plan = SavePlan.of(first, matcher, app(""), pw("new"))
        assertTrue(plan is SavePlan.New)
        // …and an entry saved without a username isn't overwritten by a named account either.
        val unnamed = SavePlan.apply(empty, SavePlan.New(), app(""), pw("x"), 1)
        assertTrue(SavePlan.of(unnamed, matcher, app("someone"), pw("y")) is SavePlan.New)
        // The user can still choose to update a specific account explicitly.
        val chosen = SavePlan.forEntry(first, first.entries.single(), pw("new"))
        assertTrue(chosen is SavePlan.Update)
        assertEquals("new", SavePlan.apply(first, chosen, app(""), pw("new"), 2).pwOf(0))
    }

    @Test
    fun pendingSaves_areOneShot_andExpire() {
        val s = web("u")
        val token = PendingSaves.put(s, nowMillis = 0)
        assertEquals(s, PendingSaves.take(token, nowMillis = 1))
        assertEquals(null, PendingSaves.take(token, nowMillis = 2))
        val stale = PendingSave(s.requester, "x", "u", pw("secret"))
        val t2 = PendingSaves.put(stale, nowMillis = 0)
        assertEquals(null, PendingSaves.take(t2, nowMillis = 6 * 60 * 1000L))
        assertEquals(null, stale.password()) // wiped
    }
}
