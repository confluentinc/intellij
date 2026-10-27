package io.confluent.intellijplugin.core.monitoring.data.updater

import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater.Companion.jitteredInterval
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BdtMonitoringUpdaterJitterTest {

    /** Random whose [nextDouble] always returns [value], to pin the jitter factor deterministically. */
    private class FixedRandom(private val value: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    @Nested
    inner class JitteredInterval {

        @Test
        fun `should stay within plus or minus 20 percent of the base interval`() {
            val random = Random(42)
            val base = 10_000
            repeat(10_000) {
                val result = jitteredInterval(base, random)
                assertTrue(result in 8_000..12_000, "jittered interval $result out of bounds")
            }
        }

        @Test
        fun `should actually vary across calls`() {
            val random = Random(42)
            val results = (1..100).map { jitteredInterval(10_000, random) }.toSet()
            assertTrue(results.size > 1, "expected jitter to produce varying intervals")
        }

        @Test
        fun `should return lower bound when random is at minimum`() {
            assertEquals(8_000, jitteredInterval(10_000, FixedRandom(0.0)))
        }

        @Test
        fun `should return base interval when random is at midpoint`() {
            assertEquals(10_000, jitteredInterval(10_000, FixedRandom(0.5)))
        }

        @Test
        fun `should stay below upper bound when random is at maximum`() {
            val result = jitteredInterval(10_000, FixedRandom(Math.nextDown(1.0)))
            assertTrue(result in 11_999..12_000, "unexpected upper-bound result $result")
        }

        @Test
        fun `should never return less than one millisecond`() {
            assertEquals(1, jitteredInterval(1, FixedRandom(0.0)))
        }
    }
}
