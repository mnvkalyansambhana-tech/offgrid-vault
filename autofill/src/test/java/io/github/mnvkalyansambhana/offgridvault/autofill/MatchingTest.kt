package io.github.mnvkalyansambhana.offgridvault.autofill

import android.text.InputType
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AeadKey
import io.github.mnvkalyansambhana.offgridvault.core.crypto.AesGcm
import io.github.mnvkalyansambhana.offgridvault.core.vault.VaultContent
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Entry
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.LinkedApp
import io.github.mnvkalyansambhana.offgridvault.core.vault.proto.Vault
import okio.ByteString.Companion.decodeHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M7 exit tests that don't need a phone (S13, S14, S16, S31, S32). */
class MatchingTest {

    private val matcher = Matcher()
    private val chrome = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
    private val bankCert = "11".repeat(32)
    private val content = VaultContent.from(
        Vault(
            entries = listOf(
                Entry(id = "1", title = "GitHub", urls = listOf("https://github.com")),
                Entry(id = "2", title = "Bank", urls = listOf("bank.com"), linked_apps = listOf(LinkedApp("com.bank.app", bankCert.decodeHex()))),
                Entry(id = "3", title = "Pages A", urls = listOf("a.github.io")),
            ),
        ),
        AeadKey.takeOwnership(AesGcm.newKey()),
    )

    private fun titles(r: Requester?) = r?.let { matcher.matches(content.entries, it).map { e -> e.title } }

    @Test
    fun app_needsPackageAndPinnedCert() {
        assertEquals(listOf("Bank"), titles(matcher.identify("com.bank.app", setOf(bankCert), null)))
        // Sideloaded app with the same package name, different cert → nothing (S13).
        assertEquals(emptyList<String>(), titles(matcher.identify("com.bank.app", setOf("22".repeat(32)), null)))
    }

    @Test
    fun nonAllowlistedBrowserClaimingADomain_isTreatedAsAnApp() {
        val r = matcher.identify("com.fake.browser", setOf("33".repeat(32)), "bank.com")
        assertTrue(r is Requester.App)
        assertEquals(emptyList<String>(), titles(r))
    }

    @Test
    fun allowlistedBrowser_matchesOnRegistrableDomain() {
        assertEquals(listOf("GitHub"), titles(matcher.identify("com.android.chrome", setOf(chrome), "gist.github.com")))
        assertEquals(listOf("Pages A"), titles(matcher.identify("com.android.chrome", setOf(chrome), "a.github.io")))
        // a.github.io never matches b.github.io (PSL private section).
        assertEquals(emptyList<String>(), titles(matcher.identify("com.android.chrome", setOf(chrome), "b.github.io")))
    }

    @Test
    fun unreadableCert_failsClosed() {
        assertNull(matcher.identify("com.bank.app", null, null))
        assertNull(matcher.identify("com.bank.app", emptySet(), null))
    }

    @Test
    fun classifier_findsLoginFields_butNotSearchBoxes() {
        val login = listOf(
            FieldInfo(1, hint = "search"),
            FieldInfo(2, idEntry = "email_input", inputType = InputType.TYPE_CLASS_TEXT),
            FieldInfo(3, inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
        )
        assertEquals(LoginFields(2, 3), LoginFieldClassifier.classify(login))

        val web = listOf(FieldInfo("u", htmlType = "text", htmlNameOrId = "login"), FieldInfo("p", htmlType = "password"))
        assertEquals(LoginFields("u", "p"), LoginFieldClassifier.classify(web))

        val usernameStep = listOf(FieldInfo(1, autofillHints = listOf("username")))
        assertEquals(LoginFields(1, null), LoginFieldClassifier.classify(usernameStep))

        // A cross-site iframe's password field never pairs with the page's own username field,
        // and the request carries the domain of the document the fields are in.
        val iframe = listOf(
            FieldInfo("u", htmlType = "email", webDomain = "good.example"),
            FieldInfo("p", htmlType = "password", webDomain = "evil.example"),
        )
        assertNull(LoginFieldClassifier.classify(iframe))
        val framedLogin = listOf(FieldInfo("p", htmlType = "password", webDomain = "evil.example"))
        assertEquals("evil.example", LoginFieldClassifier.classify(framedLogin)?.webDomain)

        val searchOnly = listOf(FieldInfo(1, hint = "search", inputType = InputType.TYPE_CLASS_TEXT))
        assertNull(LoginFieldClassifier.classify(searchOnly))
    }
}
