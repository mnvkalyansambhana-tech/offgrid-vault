package io.github.mnvkalyansambhana.offgridvault.core.vault

import org.junit.Assert.assertFalse
import org.junit.Test

/** T7: generated toString() must never print vault content (logs, crash messages, debuggers). */
class RedactionTest {

    @Test
    fun toString_hidesEverySecretField() {
        val text = vaultOf("githubsecret").toString()
        listOf(
            "githubsecret",          // title, username, urls, package name, notes
            "pw-githubsecret",       // password (bytes)
            "old-githubsecret",      // history password
            "user@githubsecret",     // username
            "com.githubsecret.app",  // linked app
        ).forEach { secret -> assertFalse("toString leaked '$secret': $text", text.contains(secret)) }
        // Bytes would otherwise print as hex; make sure the password's hex isn't there either.
        assertFalse(text.contains("pw-githubsecret".toByteArray().joinToString("") { "%02x".format(it) }))
    }
}
