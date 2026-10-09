package io.github.mnvkalyansambhana.offgridvault.autofill

/** Turns what users type into a website field, and what browsers report, into a match key (S14). */
class Domains(private val psl: PublicSuffixList = PublicSuffixList.bundled) {

    /**
     * `https://Accounts.Google.com:443/x?y` → `accounts.google.com`; `github.com/login` →
     * `github.com`. `null` if there is no usable host.
     */
    fun hostOf(urlOrHost: String): String? {
        var s = urlOrHost.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.substringAfter("://", s)
        s = s.takeWhile { it != '/' && it != '?' && it != '#' }
        s = s.substringAfterLast('@')
        s = if (s.startsWith("[")) s.substringBefore(']') + "]" else s.substringBefore(':')
        s = s.trimEnd('.')
        return s.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
    }

    /**
     * eTLD+1 for names (`login.bank.co.uk` → `bank.co.uk`); IP literals and names with no
     * registrable part (`localhost`, a bare public suffix) only match themselves exactly.
     */
    fun matchKey(urlOrHost: String): String? {
        val host = hostOf(urlOrHost) ?: return null
        if (isIpLiteral(host)) return host
        return psl.registrableDomain(host) ?: host
    }

    private fun isIpLiteral(host: String) =
        host.startsWith("[") || host.split('.').let { parts -> parts.size == 4 && parts.all { p -> p.toIntOrNull() in 0..255 } }
}
