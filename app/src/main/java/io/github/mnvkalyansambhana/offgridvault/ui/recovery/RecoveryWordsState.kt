package io.github.mnvkalyansambhana.offgridvault.ui.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mnvkalyansambhana.offgridvault.core.crypto.Bip39
import io.github.mnvkalyansambhana.offgridvault.core.crypto.wipe
import io.github.mnvkalyansambhana.offgridvault.core.vault.RecoveryCheck

/**
 * The recovery-words steps (P19, S28), shared by first-run setup and "set up later":
 * intro → words ("I've saved them") → optional tap-to-pick check → limits.
 * The entropy lives only here, in memory, until the owner seals it into the vault.
 */
class RecoveryWordsState {

    enum class Step { Intro, Words, Check, Limits }

    var step by mutableStateOf(Step.Intro)
        private set
    var words by mutableStateOf<List<String>>(emptyList())
        private set
    var saved by mutableStateOf(false)
    var check by mutableStateOf<RecoveryCheck?>(null)
        private set
    var round by mutableIntStateOf(0)
        private set
    var wrongChoice by mutableStateOf<String?>(null)
        private set

    private var entropy: ByteArray? = null

    fun showWords() {
        val fresh = Bip39.newEntropy()
        entropy?.wipe()
        entropy = fresh
        words = Bip39.toWords(fresh)
        saved = false
        step = Step.Words
    }

    fun startCheck() {
        check = RecoveryCheck(words)
        round = 0
        wrongChoice = null
        step = Step.Check
    }

    fun answer(choice: String) {
        val current = check ?: return
        if (!current.isCorrect(current.questions[round], choice)) {
            wrongChoice = choice
            return
        }
        wrongChoice = null
        if (round + 1 < current.questions.size) round++ else step = Step.Limits
    }

    fun continueWithoutCheck() {
        if (saved) step = Step.Limits
    }

    /** @return true if this handled the back press. */
    fun back(): Boolean = when (step) {
        Step.Check, Step.Limits -> {
            step = Step.Words
            true
        }
        else -> false
    }

    /** The entropy to seal, or null if words were never shown. Caller must not keep it. */
    fun entropy(): ByteArray? = entropy

    fun wipe() {
        entropy?.wipe()
        entropy = null
        words = emptyList()
        check = null
        step = Step.Intro
    }
}
