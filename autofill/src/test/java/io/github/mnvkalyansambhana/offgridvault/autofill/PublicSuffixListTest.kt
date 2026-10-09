package io.github.mnvkalyansambhana.offgridvault.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T9: the matcher passes the official PSL test file, plus our own matching rules (S14). */
class PublicSuffixListTest {

    private val psl = PublicSuffixList.bundled
    private val domains = Domains(psl)

    @Test
    fun officialTestFile() {
        val call = Regex("""checkPublicSuffix\((null|'[^']*'), (null|'[^']*')\);""")
        val lines = checkNotNull(javaClass.getResourceAsStream("/test_psl.txt")).bufferedReader().readLines()
        var checked = 0
        for (line in lines) {
            if (line.trimStart().startsWith("//")) continue // disabled upstream
            val m = call.find(line) ?: continue
            val input = m.groupValues[1].takeIf { it != "null" }?.trim('\'')
            val expected = m.groupValues[2].takeIf { it != "null" }?.trim('\'')
            val actual = input?.let(psl::registrableDomain)
            assertEquals("for $input", expected, actual)
            checked++
        }
        assertTrue("parsed the file", checked > 60)
    }

    @Test
    fun differentSitesOnSharedHostingNeverMatch() {
        assertEquals("a.github.io", domains.matchKey("https://a.github.io/login"))
        assertEquals("b.github.io", domains.matchKey("b.github.io"))
        assertEquals("bank.co.uk", domains.matchKey("login.bank.co.uk"))
        assertEquals("bank.co.uk", domains.matchKey("https://www.BANK.co.uk./x"))
    }

    @Test
    fun hostExtraction() {
        assertEquals("accounts.google.com", domains.hostOf("https://user:pw@Accounts.Google.com:443/x?y#z"))
        assertEquals("github.com", domains.hostOf("github.com/login"))
        assertEquals("192.168.1.1", domains.matchKey("http://192.168.1.1:8080/admin"))
        assertEquals("localhost", domains.matchKey("localhost"))
        assertEquals("github.io", domains.matchKey("github.io")) // a bare suffix only matches itself
        assertNull(domains.hostOf("   "))
    }

    @Test
    fun browserAllowlist_needsPackageAndCert() {
        val list = BrowserAllowlist.bundled
        val chrome = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
        assertTrue(list.isBrowser("com.android.chrome", setOf(chrome)))
        assertTrue(!list.isBrowser("com.android.chrome", setOf("00".repeat(32)))) // sideloaded fake
        assertTrue(!list.isBrowser("com.evil.browser", setOf(chrome)))
        assertTrue(!list.isBrowser("com.android.browser", setOf("c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025")))
    }
}
