package io.github.mnvkalyansambhana.offgridvault.autofill

import io.github.mnvkalyansambhana.offgridvault.core.vault.EntryView

/** Who is asking to be filled, after the S13/S14 checks. */
sealed interface Requester {
    /** An app, identified by package + one of its current signing certs (hex SHA-256), S13. */
    data class App(val packageName: String, val certs: Set<String>) : Requester

    /** An allowlisted browser (S31) showing [domainKey] (eTLD+1, S14). */
    data class Web(val browserPackage: String, val domainKey: String, val host: String) : Requester
}

/** S13–S16 matching. Pure: no Android calls, unit-tested. */
class Matcher(
    private val domains: Domains = Domains(),
    private val browsers: BrowserAllowlist = BrowserAllowlist.bundled,
) {
    /**
     * @param certs the requester's current signing certs, or `null` if they couldn't be read —
     *   then nothing is trusted and nothing is offered (S32, fail closed).
     * @param webDomain as reported by the view structure; trusted only from an allowlisted browser.
     */
    fun identify(packageName: String, certs: Set<String>?, webDomain: String?): Requester? {
        if (certs.isNullOrEmpty()) return null
        if (browsers.isBrowser(packageName, certs)) {
            // A browser's own UI (no web page) is just an app; a page gets website matching.
            val host = webDomain?.let(domains::hostOf) ?: return Requester.App(packageName, certs)
            val key = domains.matchKey(host) ?: return null
            return Requester.Web(packageName, key, host)
        }
        // S14: a non-browser claiming a web domain is treated as the app it is.
        return Requester.App(packageName, certs)
    }

    /** Only entries that match; never anything else (S16). Sorted by title. */
    fun matches(entries: List<EntryView>, requester: Requester): List<EntryView> = entries.filter { e ->
        when (requester) {
            is Requester.App -> e.linkedApps.any { link ->
                link.package_name == requester.packageName && link.cert_sha256.hex() in requester.certs
            }
            is Requester.Web -> e.urls.any { domains.matchKey(it) == requester.domainKey }
        }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}
