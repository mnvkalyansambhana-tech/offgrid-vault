package io.github.mnvkalyansambhana.offgridvault.core.vault

import io.github.mnvkalyansambhana.offgridvault.core.vault.PinPolicy.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinPolicyTest {

    private fun check(pin: String) = PinPolicy.check(pin.toCharArray())

    @Test
    fun formatRules() {
        assertEquals(Problem.WRONG_LENGTH, check("12345"))
        assertEquals(Problem.WRONG_LENGTH, check("1234567"))
        assertEquals(Problem.NOT_DIGITS, check("12a456"))
    }

    @Test
    fun patternsAreBlocked() {
        listOf(
            "000000", "777777",           // all same
            "123456", "456789", "890123", // ascending, incl. wrap
            "654321", "987654", "210987", // descending, incl. wrap
            "121212", "696969",           // repeated pair
            "123123", "520520",           // repeated triple
            "123321", "112211",           // mirrored
        ).forEach { assertEquals(it, Problem.TOO_COMMON, check(it)) }
    }

    @Test
    fun curatedListIsBlocked() {
        listOf("112233", "147258", "159753", "258456", "314159", "102030").forEach {
            assertEquals(it, Problem.TOO_COMMON, check(it))
        }
    }

    @Test
    fun ordinaryPinsAreAllowed() {
        listOf("483920", "271946", "905317", "618204", "350172").forEach { assertNull(it, check(it)) }
    }

    @Test
    fun blocksOnlyASmallShareOfAllPins() {
        val blocked = (0 until 1_000_000).count { check("%06d".format(it)) != null }
        // ~1,000 repeated triples + ~1,000 mirrored + runs/pairs/list: about 0.2% of all PINs.
        assert(blocked in 1_000..3_000) { "blocked $blocked" }
    }
}
