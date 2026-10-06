package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5: S9 session sealing, editing rules (C15 history, P12 delete), search. */
class VaultContentTest {

    private fun sessionKey() = AeadKey.takeOwnership(AesGcm.newKey())
    private fun draft(id: String?, title: String, password: String, notes: String = "") =
        EntryDraft(id, title, "user@$title", listOf("$title.example"), password.toByteArray(), notes.toByteArray())

    private fun VaultContent.passwordOf(id: String) = String(reveal(entry(id)!!.password))

    @Test
    fun secretsAreSealedNotStoredInClear() {
        val content = VaultContent.from(vaultOf("github"), sessionKey())
        val entry = content.entries.single()
        assertEquals("pw-github", String(content.reveal(entry.password)))
        assertEquals("██", entry.password.toString())
        assertFalse(entry.toString().contains("pw-github"))
        // The sealed blob is ciphertext, not the password bytes.
        assertFalse(String(entry.password.blob, Charsets.ISO_8859_1).contains("pw-github"))
    }

    @Test
    fun sessionKeyClosed_secretsUnreadable() {
        val key = sessionKey()
        val content = VaultContent.from(vaultOf("github"), key)
        key.close()
        assertThrows(IllegalStateException::class.java) { content.reveal(content.entries.single().password) }
    }

    @Test
    fun roundTrip_throughToVault() {
        val original = vaultOf("github", "bank")
        val content = VaultContent.from(original, sessionKey())
        assertEquals(original.entries.sortedBy { it.id }, content.toVault().entries.sortedBy { it.id })
    }

    @Test
    fun add_thenEdit_pushesOldPasswordToHistory_newestFirst_maxFive() {
        var content = VaultContent.from(Vault(), sessionKey())
        content = content.upsert(draft(null, "github", "p0"), nowMillis = 0)
        val id = content.entries.single().id
        (1..7).forEach { content = content.upsert(draft(id, "github", "p$it"), nowMillis = it.toLong()) }

        val entry = content.entry(id)!!
        assertEquals("p7", content.passwordOf(id))
        assertEquals(VaultContent.MAX_HISTORY, entry.history.size)
        assertEquals(listOf("p6", "p5", "p4", "p3", "p2"), entry.history.map { String(content.reveal(it.password)) })
        assertEquals(0L, entry.createdAtMillis)
        assertEquals(7L, entry.updatedAtMillis)
    }

    @Test
    fun editWithoutPasswordChange_keepsHistory() {
        var content = VaultContent.from(Vault(), sessionKey()).upsert(draft(null, "a", "same"), 0)
        val id = content.entries.single().id
        content = content.upsert(draft(id, "renamed", "same"), 1)
        assertTrue(content.entry(id)!!.history.isEmpty())
        assertEquals("renamed", content.entry(id)!!.title)
    }

    @Test
    fun clearHistory_andDelete() {
        var content = VaultContent.from(Vault(), sessionKey()).upsert(draft(null, "a", "1"), 0)
        val id = content.entries.single().id
        content = content.upsert(draft(id, "a", "2"), 1)
        assertEquals(1, content.entry(id)!!.history.size)
        content = content.clearHistory(id)
        assertTrue(content.entry(id)!!.history.isEmpty())
        content = content.delete(id)
        assertNull(content.entry(id))
        assertTrue(content.toVault().entries.isEmpty())
    }

    @Test
    fun search_matchesTitleUsernameUrl_caseInsensitive_sorted() {
        var content = VaultContent.from(Vault(), sessionKey())
        listOf("Zebra", "apple", "Mango").forEach { content = content.upsert(draft(null, it, "x"), 0) }
        assertEquals(listOf("apple", "Mango", "Zebra"), content.search("").map { it.title })
        assertEquals(listOf("Mango"), content.search("MAN").map { it.title })
        assertEquals(listOf("Zebra"), content.search("user@zebra").map { it.title })
        assertEquals(listOf("apple"), content.search("apple.example").map { it.title })
    }

    @Test
    fun notes_areSealedToo() {
        var content = VaultContent.from(Vault(), sessionKey()).upsert(draft(null, "a", "pw", notes = "backup code 1234"), 0)
        val entry = content.entries.single()
        assertEquals("backup code 1234", String(content.reveal(entry.notes)))
        assertFalse(String(entry.notes.blob, Charsets.ISO_8859_1).contains("1234"))
    }
}
