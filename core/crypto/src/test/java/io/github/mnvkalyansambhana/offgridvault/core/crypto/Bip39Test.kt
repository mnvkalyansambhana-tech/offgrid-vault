package io.github.mnvkalyansambhana.offgridvault.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Bip39Test {

    /** Official BIP-39 (Trezor) 128-bit vectors; re-derived independently with Python + hashlib. */
    private val vectors = mapOf(
        "00000000000000000000000000000000" to
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f" to
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
        "80808080808080808080808080808080" to
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
        "ffffffffffffffffffffffffffffffff" to
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "9e885d952ad362caeb4efe34a8e91bd2" to
            "ozone drill grab fiber curtain grace pudding thank cruise elder eight picnic",
        "c0ba5a8e914111210f2bd131f3d5e08d" to
            "scheme spot photo card baby mountain device kick cradle pact join borrow",
    )

    @Test
    fun wordlist_isOfficialAndComplete() {
        assertEquals(2048, Bip39.wordlist.size)
        assertEquals("abandon", Bip39.wordlist.first())
        assertEquals("zoo", Bip39.wordlist.last())
    }

    @Test
    fun officialVectors_encode() {
        vectors.forEach { (entropy, phrase) -> assertEquals(phrase, Bip39.toWords(entropy.hex()).joinToString(" ")) }
    }

    @Test
    fun officialVectors_decode() {
        vectors.forEach { (entropy, phrase) -> assertEquals(entropy, Bip39.toEntropy(phrase.split(" ")).toHex()) }
    }

    @Test
    fun randomEntropy_roundTrips() {
        repeat(200) {
            val entropy = Bip39.newEntropy()
            assertArrayEquals(entropy, Bip39.toEntropy(Bip39.toWords(entropy)))
        }
    }

    @Test
    fun typedInput_isNormalised() {
        val words = vectors.getValue("7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f").split(" ").map { "  ${it.uppercase()} " }
        assertEquals("7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f", Bip39.toEntropy(words).toHex())
    }

    @Test
    fun badChecksum_isRejected() {
        // Valid words, wrong last word → checksum mismatch.
        val words = vectors.getValue("00000000000000000000000000000000").split(" ").toMutableList()
        words[11] = "abandon"
        val e = assertThrows(InvalidMnemonicException::class.java) { Bip39.toEntropy(words) }
        assertEquals(InvalidMnemonicException.Reason.BAD_CHECKSUM, e.reason)
    }

    @Test
    fun unknownWord_reportsPositionButNotTheWord() {
        val words = vectors.getValue("80808080808080808080808080808080").split(" ").toMutableList()
        words[2] = "notaword"
        val e = assertThrows(InvalidMnemonicException::class.java) { Bip39.toEntropy(words) }
        assertEquals(InvalidMnemonicException.Reason.UNKNOWN_WORD, e.reason)
        assertEquals(2, e.position)
        assertFalse(e.message!!.contains("notaword"))
    }

    @Test
    fun wrongWordCount_isRejected() {
        val e = assertThrows(InvalidMnemonicException::class.java) { Bip39.toEntropy(List(11) { "zoo" }) }
        assertEquals(InvalidMnemonicException.Reason.WRONG_WORD_COUNT, e.reason)
    }

    @Test
    fun isValidWord() {
        assertTrue(Bip39.isValidWord(" Zoo "))
        assertFalse(Bip39.isValidWord("zooo"))
    }
}
