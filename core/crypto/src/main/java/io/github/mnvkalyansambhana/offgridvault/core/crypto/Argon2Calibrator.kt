package io.github.mnvkalyansambhana.offgridvault.core.crypto

/**
 * Chooses Argon2id iterations for *this* phone (C14): memory fixed at 64 MiB, iterations scaled
 * so one derivation takes about [TARGET_MILLIS], never below [Argon2Params.MIN_ITERATIONS].
 * Safe because the vault never leaves the device it was calibrated on.
 *
 * Runs on throwaway random input, never on the user's PIN. Takes roughly 2 × the floor cost,
 * so call it off the main thread.
 */
class Argon2Calibrator(
    private val argon2: Argon2id,
    private val nanoClock: () -> Long = System::nanoTime,
) {

    data class Result(val params: Argon2Params, val floorMillis: Long)

    fun calibrate(targetMillis: Long = TARGET_MILLIS, maxIterations: Int = MAX_CALIBRATED_ITERATIONS): Result {
        require(targetMillis > 0) { "targetMillis must be positive" }
        val floor = Argon2Params(Argon2Params.MIN_ITERATIONS)
        // Two runs, keep the faster: the first one also pays for page-faulting 64 MiB.
        val floorNanos = minOf(time(floor), time(floor))
        val perIterationNanos = (floorNanos / floor.iterations).coerceAtLeast(1)
        val iterations = (targetMillis * NANOS_PER_MILLI / perIterationNanos)
            .coerceIn(Argon2Params.MIN_ITERATIONS.toLong(), maxIterations.toLong())
            .toInt()
        return Result(Argon2Params(iterations), floorNanos / NANOS_PER_MILLI)
    }

    private fun time(params: Argon2Params): Long {
        val password = Randomness.bytes(16)
        val salt = Argon2id.newSalt()
        val start = nanoClock()
        argon2.deriveKey(password, salt, params).wipe()
        return nanoClock() - start
    }

    companion object {
        /** Middle of the 0.5–1 s unlock budget (C14). */
        const val TARGET_MILLIS = 750L
        const val MAX_CALIBRATED_ITERATIONS = 16
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
