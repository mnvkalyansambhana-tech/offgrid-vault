package io.github.mnvkalyansambhana.offgridvault.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class Argon2CalibratorTest {

    /** Fake engine whose cost is [millisPerIteration] per pass, on a fake clock. */
    private fun calibrator(millisPerIteration: Long): Argon2Calibrator {
        var now = 0L
        val engine = Argon2Engine { out, _, _, iterations, _ ->
            now += iterations * millisPerIteration * 1_000_000
            out.fill(1)
            true
        }
        return Argon2Calibrator(Argon2id(engine)) { now }
    }

    @Test
    fun scalesIterationsToTarget() {
        // 100 ms per pass → 750 ms target → 7 passes.
        assertEquals(7, calibrator(100).calibrate().params.iterations)
    }

    @Test
    fun slowPhone_neverGoesBelowFloor() {
        assertEquals(Argon2Params.MIN_ITERATIONS, calibrator(900).calibrate().params.iterations)
    }

    @Test
    fun fastPhone_isCapped() {
        assertEquals(Argon2Calibrator.MAX_CALIBRATED_ITERATIONS, calibrator(1).calibrate().params.iterations)
    }

    @Test
    fun alwaysUses64MiB_andReportsFloorTime() {
        val result = calibrator(100).calibrate()
        assertEquals(Argon2Params.MEMORY_KIB, result.params.memoryKiB)
        assertEquals(200, result.floorMillis)
    }

    @Test
    fun realLibsodium_producesValidParams() {
        val params = Argon2Calibrator(jvmSodiumArgon2).calibrate().params
        assert(params.iterations in Argon2Params.MIN_ITERATIONS..Argon2Calibrator.MAX_CALIBRATED_ITERATIONS)
    }
}
