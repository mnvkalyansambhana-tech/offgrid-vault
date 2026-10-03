package io.github.mnvkalyansambhana.offgridvault.core.crypto

import java.security.MessageDigest
import java.util.Locale

/**
 * 12-word BIP-39 recovery phrase (S5, T10): 128 bits of entropy + 4-bit SHA-256 checksum,
 * official English wordlist. Only the encoding is BIP-39 — the entropy feeds [RecoveryKdf],
 * not the BIP-39 PBKDF2 seed.
 *
 * Words are returned as `String`s because they must be displayed; they cannot be wiped (S25).
 */
object Bip39 {
    const val WORD_COUNT = 12
    const val ENTROPY_BYTES = 16
    private const val BITS_PER_WORD = 11
    private const val CHECKSUM_BITS = 4
    private const val WORDLIST_SIZE = 2048

    /** SHA-256 of the official english.txt (bitcoin/bips). Checked on every load. */
    private const val WORDLIST_SHA256 = "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda"

    /** Absolute path: R8 may move this class to another package in release builds. */
    private const val WORDLIST_RESOURCE = "/io/github/mnvkalyansambhana/offgridvault/core/crypto/bip39-english.txt"

    val wordlist: List<String> by lazy(::loadWordlist)
    private val index: Map<String, Int> by lazy { wordlist.withIndex().associate { (i, w) -> w to i } }

    /** Fresh entropy for a new vault. The caller wipes it once the KEK is derived. */
    fun newEntropy(): ByteArray = Randomness.bytes(ENTROPY_BYTES)

    fun toWords(entropy: ByteArray): List<String> {
        require(entropy.size == ENTROPY_BYTES) { "entropy must be $ENTROPY_BYTES bytes" }
        // entropy (128 bits) followed by the checksum (top 4 bits of SHA-256) = 132 bits = 12 × 11.
        val bits = entropy.copyOf(ENTROPY_BYTES + 1)
        bits[ENTROPY_BYTES] = checksumByte(entropy)
        return try {
            List(WORD_COUNT) { wordlist[bits.readBits(it * BITS_PER_WORD, BITS_PER_WORD)] }
        } finally {
            bits.wipe()
        }
    }

    /**
     * Parses typed words (case and surrounding whitespace ignored).
     * @throws InvalidMnemonicException with a reason and position — never the word itself.
     */
    fun toEntropy(words: List<String>): ByteArray {
        if (words.size != WORD_COUNT) throw InvalidMnemonicException(InvalidMnemonicException.Reason.WRONG_WORD_COUNT)
        val bits = ByteArray(ENTROPY_BYTES + 1)
        try {
            words.forEachIndexed { position, word ->
                val wordIndex = index[normalize(word)]
                    ?: throw InvalidMnemonicException(InvalidMnemonicException.Reason.UNKNOWN_WORD, position)
                bits.writeBits(position * BITS_PER_WORD, BITS_PER_WORD, wordIndex)
            }
            val entropy = bits.copyOf(ENTROPY_BYTES)
            val expected = byteArrayOf(checksumByte(entropy))
            val actual = byteArrayOf(bits[ENTROPY_BYTES])
            if (!MessageDigest.isEqual(expected, actual)) {
                entropy.wipe()
                throw InvalidMnemonicException(InvalidMnemonicException.Reason.BAD_CHECKSUM)
            }
            return entropy
        } finally {
            bits.wipe()
        }
    }

    /** For checking each word as the user types it (UI: "word 3 isn't on the word list"). */
    fun isValidWord(word: String): Boolean = normalize(word) in index

    private fun normalize(word: String) = word.trim().lowercase(Locale.ROOT)

    /** Top [CHECKSUM_BITS] bits of SHA-256(entropy), left-aligned in a byte. */
    private fun checksumByte(entropy: ByteArray): Byte {
        val digest = MessageDigest.getInstance("SHA-256").digest(entropy)
        return (digest[0].toInt() and (0xFF shl (8 - CHECKSUM_BITS)) and 0xFF).toByte().also { digest.wipe() }
    }

    private fun loadWordlist(): List<String> {
        val bytes = Bip39::class.java.getResourceAsStream(WORDLIST_RESOURCE)
            ?.use { it.readBytes() }
            ?: error("BIP-39 wordlist missing")
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == WORDLIST_SHA256) { "BIP-39 wordlist corrupted" }
        return bytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
            .also { check(it.size == WORDLIST_SIZE) { "BIP-39 wordlist has ${it.size} words" } }
    }

    /** Reads [count] bits starting at bit [offset] (big-endian, MSB first). */
    private fun ByteArray.readBits(offset: Int, count: Int): Int {
        var value = 0
        for (i in offset until offset + count) {
            value = (value shl 1) or ((this[i / 8].toInt() shr (7 - i % 8)) and 1)
        }
        return value
    }

    /** Writes the low [count] bits of [value] starting at bit [offset] (big-endian). */
    private fun ByteArray.writeBits(offset: Int, count: Int, value: Int) {
        for (i in 0 until count) {
            val bit = (value shr (count - 1 - i)) and 1
            val position = offset + i
            if (bit == 1) this[position / 8] = (this[position / 8].toInt() or (0x80 ushr (position % 8))).toByte()
        }
    }
}

class InvalidMnemonicException(val reason: Reason, val position: Int? = null) :
    IllegalArgumentException("Invalid recovery words: $reason" + (position?.let { " at word ${it + 1}" } ?: "")) {
    enum class Reason { WRONG_WORD_COUNT, UNKNOWN_WORD, BAD_CHECKSUM }
}
