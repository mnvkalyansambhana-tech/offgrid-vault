package io.github.mnvkalyansambhana.offgridvault.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretsTest {

    @Test
    fun wipe_zeroesArrays() {
        val bytes = byteArrayOf(1, 2, 3).also { it.wipe() }
        assertTrue(bytes.all { it == 0.toByte() })
        val chars = charArrayOf('p', 'i', 'n').also { it.wipe() }
        assertTrue(chars.all { it == '\u0000' })
    }

    @Test
    fun useAndWipe_wipesEvenOnException() {
        val secret = byteArrayOf(9, 9)
        runCatching { secret.useAndWipe { error("boom") } }
        assertTrue(secret.all { it == 0.toByte() })
    }

    @Test
    fun randomness_lengthsAndBounds() {
        assertEquals(32, Randomness.bytes(32).size)
        assertTrue((1..1000).map { Randomness.int(10) }.all { it in 0 until 10 })
        assertThrows(IllegalArgumentException::class.java) { Randomness.bytes(0) }
    }
}
