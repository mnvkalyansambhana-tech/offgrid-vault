package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Randomness

/**
 * Optional "Check my words" (P19): a few rounds of "which is word #n?" with 4 choices — tapping,
 * no typing. Decoys come from the official wordlist and never from the user's own words.
 */
class RecoveryCheck(private val words: List<String>, rounds: Int = 3) {

    data class Question(val position: Int, val options: List<String>)

    val questions: List<Question> = pickPositions(rounds).map { position ->
        val decoys = mutableSetOf<String>()
        while (decoys.size < 3) {
            val candidate = Bip39.wordlist[Randomness.int(Bip39.wordlist.size)]
            if (candidate !in words) decoys += candidate
        }
        Question(position, shuffled(decoys.toList() + words[position]))
    }

    fun isCorrect(question: Question, choice: String): Boolean = choice == words[question.position]

    private fun pickPositions(rounds: Int): List<Int> {
        require(rounds in 1..words.size)
        val picked = linkedSetOf<Int>()
        while (picked.size < rounds) picked += Randomness.int(words.size)
        return picked.sorted()
    }

    /** Fisher–Yates with the app's CSPRNG. */
    private fun shuffled(items: List<String>): List<String> {
        val list = items.toMutableList()
        for (i in list.indices.reversed()) {
            val j = Randomness.int(i + 1)
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        }
        return list
    }
}
