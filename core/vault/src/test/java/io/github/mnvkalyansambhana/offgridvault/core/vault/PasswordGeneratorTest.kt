package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.vault.PasswordGenerator.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun defaults_are20Chars_withEveryClass() {
        repeat(500) {
            val p = String(PasswordGenerator.generate())
            assertEquals(20, p.length)
            assertTrue(p.any { it.isUpperCase() } && p.any { it.isLowerCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun noProblemCharacters() {
        repeat(500) {
            val p = String(PasswordGenerator.generate(Options(length = 64)))
            assertTrue(p.none { it == ' ' || it == '"' || it == '\'' || it == '\\' || it == '`' })
        }
    }

    @Test
    fun avoidLookAlikes() {
        repeat(500) {
            val p = String(PasswordGenerator.generate(Options(length = 64, avoidLookAlikes = true)))
            assertTrue(p.none { it in "0O1lI|" })
        }
    }

    @Test
    fun onlySelectedClasses() {
        val p = String(PasswordGenerator.generate(Options(length = 40, upper = false, lower = false, symbols = false)))
        assertTrue(p.all { it.isDigit() })
    }

    @Test
    fun limitsAndAtLeastOneClass() {
        assertThrows(IllegalArgumentException::class.java) { Options(length = 7) }
        assertThrows(IllegalArgumentException::class.java) { Options(length = 65) }
        assertThrows(IllegalArgumentException::class.java) { Options(upper = false, lower = false, digits = false, symbols = false) }
    }

    @Test
    fun entropy_isReported() {
        // 26+26+10+25 = 87 symbols; 20 × log2(87) ≈ 128.8
        assertEquals(87, Options().alphabetSize)
        assertEquals(128, Options().entropyBits)
    }

    @Test
    fun distribution_isRoughlyUniform() {
        // Digits only: each of 10 digits should appear ~10% of the time (no modulo bias).
        val counts = IntArray(10)
        repeat(2_000) {
            PasswordGenerator.generate(Options(length = 50, upper = false, lower = false, symbols = false)).forEach { counts[it - '0']++ }
        }
        val expected = 100_000 / 10.0
        counts.forEach { assertTrue("count $it vs $expected", kotlin.math.abs(it - expected) < expected * 0.05) }
    }
}
