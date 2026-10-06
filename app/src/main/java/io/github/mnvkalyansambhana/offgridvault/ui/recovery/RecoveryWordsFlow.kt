package io.github.mnvkalyansambhana.offgridvault.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mnvkalyansambhana.offgridvault.ui.components.Body
import io.github.mnvkalyansambhana.offgridvault.ui.components.HeroBand
import io.github.mnvkalyansambhana.offgridvault.ui.components.InverseCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.Label
import io.github.mnvkalyansambhana.offgridvault.ui.components.PopButton
import io.github.mnvkalyansambhana.offgridvault.ui.components.SharpCard
import io.github.mnvkalyansambhana.offgridvault.ui.components.TwoToneScreen
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridColors
import io.github.mnvkalyansambhana.offgridvault.ui.theme.OffGridType

/**
 * Recovery-words screens (P19). [onSkip] non-null shows "Set up later" on the intro (setup
 * only). [stepLabel] lets setup say "Step 2 of 4" while the later flow says "Recovery words".
 */
@Composable
fun RecoveryWordsFlow(
    state: RecoveryWordsState,
    onUnderstood: () -> Unit,
    onSkip: (() -> Unit)?,
    stepLabel: (RecoveryWordsState.Step) -> String,
) {
    when (state.step) {
        RecoveryWordsState.Step.Intro -> Intro(stepLabel(state.step), state::showWords, onSkip)
        RecoveryWordsState.Step.Words -> Words(state, stepLabel(state.step))
        RecoveryWordsState.Step.Check -> Check(state, stepLabel(state.step))
        RecoveryWordsState.Step.Limits -> Limits(stepLabel(state.step), onUnderstood)
    }
}

@Composable
private fun Intro(label: String, onShow: () -> Unit, onSkip: (() -> Unit)?) {
    TwoToneScreen(
        scrollable = false,
        hero = {
            HeroBand(
                headline = "your recovery words.",
                label = label,
                supporting = "Next you'll see 12 words. They're the only way back in if you forget your PIN.",
            )
        },
    ) {
        Numbered(1, "write them on paper — screenshots are blocked")
        Numbered(2, "keep them somewhere safe and private")
        Numbered(3, "shown only once — they never change")
        Spacer(Modifier.weight(1f))
        PopButton("Show my words", onClick = onShow, modifier = Modifier.fillMaxWidth())
        if (onSkip != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onSkip),
                contentAlignment = Alignment.Center,
            ) {
                Label("Set up later", color = OffGridColors.TextOnLight2)
            }
        }
    }
}

@Composable
private fun Numbered(n: Int, text: String) {
    SharpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(OffGridColors.Ink), contentAlignment = Alignment.Center) {
                BasicText("$n", style = OffGridType.Secret.copy(color = OffGridColors.TextOnDark))
            }
            Spacer(Modifier.width(14.dp))
            Body(text, style = OffGridType.BodyStrong)
        }
    }
}

@Composable
private fun Words(state: RecoveryWordsState, label: String) {
    TwoToneScreen(
        hero = {
            HeroBand(headline = "your 12 words.", label = label) {
                Box(Modifier.fillMaxWidth().border(1.dp, OffGridColors.Mint).padding(10.dp), contentAlignment = Alignment.Center) {
                    Label("Shown once · no copy · screenshots blocked", color = OffGridColors.Mint, style = OffGridType.LabelSmall)
                }
            }
        },
    ) {
        state.words.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, word ->
                    SharpCard(Modifier.weight(1f)) {
                        Row {
                            BasicText("%02d".format(row * 2 + col + 1), style = OffGridType.Secret.copy(color = OffGridColors.Green))
                            Spacer(Modifier.width(12.dp))
                            BasicText(word, style = OffGridType.Secret.copy(color = OffGridColors.Ink))
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .toggleable(value = state.saved, role = Role.Checkbox, onValueChange = { state.saved = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).border(2.dp, OffGridColors.Ink).background(if (state.saved) OffGridColors.Ink else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (state.saved) BasicText("✓", style = OffGridType.BodyStrong.copy(color = OffGridColors.Mint))
            }
            Spacer(Modifier.width(12.dp))
            Body("I've saved them somewhere safe", style = OffGridType.BodyStrong)
        }
        PopButton("Continue", onClick = state::continueWithoutCheck, enabled = state.saved, modifier = Modifier.fillMaxWidth())
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, enabled = state.saved, onClick = state::startCheck),
            contentAlignment = Alignment.Center,
        ) {
            Label("Check my words (optional)", color = if (state.saved) OffGridColors.Green else OffGridColors.InputBorder)
        }
    }
}

@Composable
private fun Check(state: RecoveryWordsState, label: String) {
    val check = state.check ?: return
    val question = check.questions[state.round]
    TwoToneScreen(
        hero = {
            HeroBand(
                headline = "which is word #${question.position + 1}?",
                label = "$label · check ${state.round + 1} of ${check.questions.size}",
                supporting = "look at your paper and tap it",
            )
        },
    ) {
        question.options.forEach { option ->
            val wrong = option == state.wrongChoice
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(OffGridColors.Card)
                    .border(if (wrong) 2.dp else 1.dp, if (wrong) OffGridColors.CoralDeep else OffGridColors.PaperHairline)
                    .clickable(role = Role.Button) { state.answer(option) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(option, style = OffGridType.Secret.copy(color = OffGridColors.Ink))
            }
        }
        state.wrongChoice?.let { Label("Not that one — check your paper", color = OffGridColors.CoralDeep, style = OffGridType.LabelSmall) }
    }
}

@Composable
private fun Limits(label: String, onUnderstood: () -> Unit) {
    TwoToneScreen(hero = { HeroBand(headline = "before you go.", label = label) }) {
        Limit("✓", OffGridColors.Mint, OffGridColors.Ink, "They unlock your vault on this phone if you forget your PIN.")
        Limit("✕", OffGridColors.CoralDeep, OffGridColors.TextOnDark, "They won't restore your passwords on a new phone or after uninstalling. Your vault exists only here.")
        Limit("!", OffGridColors.Amber, OffGridColors.Ink, "Anyone with this phone and these words can open your vault. Never share them.")
        InverseCard(Modifier.fillMaxWidth()) {
            Label("Never asked", color = OffGridColors.Mint, style = OffGridType.LabelSmall)
            Body("OffGrid Vault will never ask you for these words. No account, no support login, nothing online.", color = OffGridColors.TextOnDarkBody)
        }
        PopButton("I understand", onClick = onUnderstood, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Limit(mark: String, markBg: Color, markFg: Color, text: String) {
    SharpCard(Modifier.fillMaxWidth()) {
        Row {
            Box(Modifier.size(28.dp).background(markBg), contentAlignment = Alignment.Center) {
                BasicText(mark, style = OffGridType.BodyStrong.copy(color = markFg))
            }
            Spacer(Modifier.width(14.dp))
            Body(text)
        }
    }
}
