package io.github.mnvkalyansambhana.offgridvault.autofill

import java.net.IDN

/**
 * Registrable domain (eTLD+1) lookup over the bundled official Public Suffix List (S14, T9),
 * ICANN and private sections alike — so `a.github.io` and `b.github.io` are different sites.
 * Implements the algorithm at publicsuffix.org/list ("prevailing rule"; exceptions win; default
 * rule `*`) and is checked against the official test file.
 */
class PublicSuffixList(rules: Sequence<String>) {

    private val exact = HashSet<String>()
    private val wildcard = HashSet<String>() // "*.ck" stored as "ck"
    private val exception = HashSet<String>() // "!www.ck" stored as "www.ck"

    init {
        for (raw in rules) {
            val line = raw.trim().substringBefore(' ')
            if (line.isEmpty() || line.startsWith("//")) continue
            when {
                line.startsWith("!") -> toAscii(line.substring(1))?.let(exception::add)
                line.startsWith("*.") -> toAscii(line.substring(2))?.let(wildcard::add)
                else -> toAscii(line)?.let(exact::add)
            }
        }
    }

    /**
     * The registrable domain of [host] (in the same script it was given: punycode in, punycode
     * out; Unicode in, Unicode out), or `null` if [host] is itself a public suffix or malformed.
     */
    fun registrableDomain(host: String): String? {
        val input = host.lowercase()
        if (input.isEmpty() || input.startsWith(".") || input.endsWith(".") || ".." in input) return null
        val ascii = toAscii(input) ?: return null
        val labels = ascii.split('.')
        val suffixLength = suffixLength(labels)
        if (labels.size <= suffixLength) return null
        val registrable = labels.takeLast(suffixLength + 1).joinToString(".")
        return if (ascii == input) registrable else IDN.toUnicode(registrable)
    }

    private fun suffixLength(labels: List<String>): Int {
        val n = labels.size
        for (i in 0 until n) {
            if (labels.subList(i, n).joinToString(".") in exception) return n - i - 1
        }
        for (i in 0 until n) { // longest candidate first
            val candidate = labels.subList(i, n).joinToString(".")
            if (candidate in exact) return n - i
            if (i + 1 < n && labels.subList(i + 1, n).joinToString(".") in wildcard) return n - i
        }
        return 1 // default rule "*"
    }

    private fun toAscii(domain: String): String? = try {
        IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED).lowercase().takeIf { it.isNotEmpty() }
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        private const val RESOURCE = "/io/github/mnvkalyansambhana/offgridvault/autofill/public_suffix_list.dat"

        /** The bundled list (absolute resource path: survives R8 package moves). */
        val bundled: PublicSuffixList by lazy {
            val stream = checkNotNull(PublicSuffixList::class.java.getResourceAsStream(RESOURCE)) { "PSL missing" }
            stream.bufferedReader(Charsets.UTF_8).useLines { PublicSuffixList(it.toList().asSequence()) }
        }
    }
}
