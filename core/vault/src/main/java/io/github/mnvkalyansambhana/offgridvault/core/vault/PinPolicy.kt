package io.github.mnvkalyansambhana.offgridvault.core.vault

/**
 * App PIN rules (S2, S19, S27): exactly 6 digits, and not one of the PINs an attacker would
 * try first — with only 3 attempts (S3), blocking these removes most of the realistic risk.
 */
object PinPolicy {
    const val LENGTH = 6

    enum class Problem { WRONG_LENGTH, NOT_DIGITS, TOO_COMMON }

    /** @return why [pin] is not allowed, or `null` if it is fine. Does not keep [pin]. */
    fun check(pin: CharArray): Problem? {
        if (pin.size != LENGTH) return Problem.WRONG_LENGTH
        if (!pin.all { it in '0'..'9' }) return Problem.NOT_DIGITS
        val d = IntArray(LENGTH) { pin[it] - '0' }
        return if (isPattern(d) || String(pin) in COMMON) Problem.TOO_COMMON else null
    }

    private fun isPattern(d: IntArray): Boolean {
        val steps = (1 until LENGTH).map { (d[it] - d[it - 1] + 10) % 10 }
        return steps.all { it == 0 } || // 111111
            steps.all { it == 1 } || // 123456, 890123 (wraps)
            steps.all { it == 9 } || // 654321, 210987
            (0 until LENGTH).all { d[it] == d[it % 2] } || // 121212
            (0 until LENGTH).all { d[it] == d[it % 3] } || // 123123
            (0 until LENGTH / 2).all { d[it] == d[LENGTH - 1 - it] } // 123321
    }

    /** Well-known PINs not caught by [isPattern]: keypad shapes, number games, doubled steps. */
    private val COMMON = setOf(
        "112233", "111222", "222333", "333444", "998877", "999888", "887766", "121314", "102030",
        "100000", "000001", "100200", "200100", "123654", "654123", "123698", "123789", "789123",
        "147852", "147369", "159357", "357159", "159753", "753159", "951357", "357951", "147258",
        "258369", "258456", "852456", "789456", "456123", "321654", "963852", "741852", "852963",
        "654987", "987456", "124578", "147896", "135790", "246800", "135791", "246802", "112358",
        "314159", "271828", "123465",
    )
}
