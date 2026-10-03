package io.github.mnvkalyansambhana.offgridvault.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.mnvkalyansambhana.offgridvault.R

/** Bundled fonts only — downloadable fonts would need the network (DESIGN_SYSTEM.md §3). */
object OffGridFonts {
    val Fraunces = FontFamily(Font(R.font.fraunces_regular, FontWeight.Normal))
    val Manrope = FontFamily(
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
        Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    )
    val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))
}

/** Type scale from docs/DESIGN_SYSTEM.md §3. */
object OffGridType {
    /** Lowercase, ends with a full stop: "vault locked." */
    val Headline = TextStyle(
        fontFamily = OffGridFonts.Fraunces,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 1.05.em,
        letterSpacing = (-0.5).sp,
    )
    val HeadlineHero = Headline.copy(fontSize = 46.sp, lineHeight = 1.04.em, letterSpacing = (-1).sp)
    val HeadlineSheet = Headline.copy(fontSize = 32.sp)

    /** UPPERCASE, wide tracking: "STEP 2 OF 4". */
    val Label = TextStyle(
        fontFamily = OffGridFonts.Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 3.sp,
    )
    val LabelSmall = Label.copy(fontSize = 10.sp, letterSpacing = 2.sp)

    val Button = TextStyle(
        fontFamily = OffGridFonts.Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        letterSpacing = 3.sp,
    )

    val Body = TextStyle(
        fontFamily = OffGridFonts.Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 1.5.em,
    )
    val BodyStrong = Body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)

    /** Passwords, recovery words, package names, numbers. */
    val Secret = TextStyle(
        fontFamily = OffGridFonts.JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    )
}
