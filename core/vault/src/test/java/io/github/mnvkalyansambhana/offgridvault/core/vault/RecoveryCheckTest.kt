package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCheckTest {

    @Test
    fun questions_areWellFormed() {
        repeat(200) {
            val words = Bip39.toWords(Bip39.newEntropy())
            val check = RecoveryCheck(words)
            assertEquals(3, check.questions.size)
            assertEquals(3, check.questions.map { it.position }.toSet().size)
            check.questions.forEach { q ->
                assertEquals(4, q.options.size)
                assertEquals(4, q.options.toSet().size)
                assertTrue(words[q.position] in q.options)
                // Decoys never come from the user's own words (no confusing near-misses).
                assertEquals(1, q.options.count { it in words })
                assertTrue(check.isCorrect(q, words[q.position]))
                q.options.filter { it != words[q.position] }.forEach { assertFalse(check.isCorrect(q, it)) }
            }
        }
    }

    @Test
    fun correctAnswer_isNotAlwaysInTheSameSlot() {
        val words = Bip39.toWords(Bip39.newEntropy())
        val slots = (1..200).map { RecoveryCheck(words).questions.first().let { q -> q.options.indexOf(words[q.position]) } }
        assertEquals(4, slots.toSet().size)
    }
}
