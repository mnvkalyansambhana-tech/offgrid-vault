package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.Randomness
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import kotlin.math.log2

/**
 * Password generator (P10): every character drawn uniformly with SecureRandom (no modulo bias);
 * when several classes are selected, at least one of each is guaranteed by redrawing the whole
 * password (rejection sampling keeps the result uniform over all valid passwords).
 */
object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val DEFAULT_LENGTH = 20

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"

    /** No quotes, backslash or space: these break many sign-up forms. */
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/~"
    private const val LOOK_ALIKES = "0O1lI|"

    data class Options(
        val length: Int = DEFAULT_LENGTH,
        val upper: Boolean = true,
        val lower: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val avoidLookAlikes: Boolean = false,
    ) {
        init {
            require(length in MIN_LENGTH..MAX_LENGTH) { "length out of range" }
            require(upper || lower || digits || symbols) { "select at least one character type" }
        }

        internal val classes: List<String>
            get() = listOfNotNull(UPPER.takeIf { upper }, LOWER.takeIf { lower }, DIGITS.takeIf { digits }, SYMBOLS.takeIf { symbols })
                .map { set -> if (avoidLookAlikes) set.filterNot { it in LOOK_ALIKES } else set }

        val alphabetSize: Int get() = classes.sumOf { it.length }

        /** Strength shown in the UI: length × log2(alphabet). */
        val entropyBits: Int get() = (length * log2(alphabetSize.toDouble())).toInt()
    }

    /** The caller owns the result and should wipe it once it's in the form. */
    fun generate(options: Options = Options()): CharArray {
        val classes = options.classes
        val alphabet = classes.joinToString("")
        while (true) {
            val candidate = CharArray(options.length) { alphabet[Randomness.int(alphabet.length)] }
            if (classes.all { set -> candidate.any { it in set } }) return candidate
            candidate.wipe()
        }
    }
}
