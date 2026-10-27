package io.confluent.intellijplugin.ccloud.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CCloudRetryPolicyTest {

    /** Random whose nextDouble() always returns [value], so jitter is deterministic. */
    private class FixedRandom(private val value: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    private val noJitter = CCloudRetryPolicy(random = FixedRandom(0.0))
    private val now: Instant = Instant.parse("2015-10-21T07:28:00Z")

    @Nested
    inner class Defaults {

        @Test
        fun `should use settled default values`() {
            val policy = CCloudRetryPolicy.DEFAULT
            assertEquals(4, policy.maxAttempts)
            assertEquals(1.seconds, policy.initialBackoff)
            assertEquals(1.6, policy.multiplier)
            assertEquals(10.seconds, policy.maxBackoff)
            assertEquals(0.2, policy.jitter)
            assertEquals(60.seconds, policy.maxRetryAfter)
        }

        @Test
        fun `should reject invalid configuration`() {
            assertThrows<IllegalArgumentException> { CCloudRetryPolicy(maxAttempts = 0) }
            assertThrows<IllegalArgumentException> { CCloudRetryPolicy(multiplier = 0.5) }
            assertThrows<IllegalArgumentException> { CCloudRetryPolicy(jitter = -0.1) }
            assertThrows<IllegalArgumentException> {
                CCloudRetryPolicy(initialBackoff = 5.seconds, maxBackoff = 1.seconds)
            }
            assertThrows<IllegalArgumentException> { CCloudRetryPolicy(initialBackoff = (-1).seconds) }
            assertThrows<IllegalArgumentException> { CCloudRetryPolicy(maxRetryAfter = (-1).seconds) }
        }
    }

    @Nested
    inner class DelayFor {

        @Test
        fun `should grow exponentially by multiplier without jitter`() {
            assertEquals(1000.milliseconds, noJitter.delayFor(0))
            assertEquals(1600.milliseconds, noJitter.delayFor(1))
            assertEquals(2560.milliseconds, noJitter.delayFor(2))
            assertEquals(4096.milliseconds, noJitter.delayFor(3))
        }

        @Test
        fun `should cap backoff at maxBackoff`() {
            assertEquals(10.seconds, noJitter.delayFor(10))
            assertEquals(10.seconds, noJitter.delayFor(1_000))
        }

        @Test
        fun `should add at most jitter fraction on top of backoff`() {
            val maxJitter = CCloudRetryPolicy(random = FixedRandom(0.999_999))
            val delay = maxJitter.delayFor(0)
            assertTrue(delay >= 1.seconds, "delay $delay should be >= 1s")
            assertTrue(delay < 1200.milliseconds, "delay $delay should be < 1.2s")

            val halfJitter = CCloudRetryPolicy(random = FixedRandom(0.5))
            assertEquals(1100.milliseconds, halfJitter.delayFor(0))
        }

        @Test
        fun `should keep jittered delays within bounds for real randomness`() {
            val policy = CCloudRetryPolicy(random = Random(42))
            repeat(1_000) { i ->
                val retryIndex = i % 8
                val base = noJitter.delayFor(retryIndex)
                val delay = policy.delayFor(retryIndex)
                assertTrue(delay >= base, "delay $delay should be >= base $base")
                assertTrue(delay <= base * 1.2, "delay $delay should be <= ${base * 1.2}")
            }
        }

        @Test
        fun `should apply jitter on top of capped backoff`() {
            val policy = CCloudRetryPolicy(random = FixedRandom(0.5))
            assertEquals(11.seconds, policy.delayFor(20))
        }

        @Test
        fun `should never wait less than Retry-After`() {
            assertEquals(30.seconds, noJitter.delayFor(0, retryAfter = 30.seconds))

            val maxJitter = CCloudRetryPolicy(random = FixedRandom(0.999_999))
            repeat(5) { retryIndex ->
                assertTrue(maxJitter.delayFor(retryIndex, retryAfter = 30.seconds) >= 30.seconds)
            }
        }

        @Test
        fun `should use backoff when it exceeds Retry-After`() {
            assertEquals(2560.milliseconds, noJitter.delayFor(2, retryAfter = 1.seconds))
        }

        @Test
        fun `should jitter on top of Retry-After`() {
            val policy = CCloudRetryPolicy(random = FixedRandom(0.5))
            assertEquals(33.seconds, policy.delayFor(0, retryAfter = 30.seconds))
        }

        @Test
        fun `should clamp Retry-After to maxRetryAfter`() {
            assertEquals(60.seconds, noJitter.delayFor(0, retryAfter = 600.seconds))
        }

        @Test
        fun `should ignore negative Retry-After`() {
            assertEquals(1.seconds, noJitter.delayFor(0, retryAfter = (-5).seconds))
        }

        @Test
        fun `should return zero delay when backoff is zero and jitter disabled`() {
            val policy = CCloudRetryPolicy(initialBackoff = Duration.ZERO, jitter = 0.0)
            assertEquals(Duration.ZERO, policy.delayFor(3))
        }

        @Test
        fun `should reject negative retry index`() {
            assertThrows<IllegalArgumentException> { noJitter.delayFor(-1) }
        }
    }

    @Nested
    inner class ParseRetryAfter {

        @Test
        fun `should parse delta-seconds`() {
            assertEquals(45.seconds, noJitter.parseRetryAfter("45", now))
            assertEquals(Duration.ZERO, noJitter.parseRetryAfter("0", now))
            assertEquals(5.seconds, noJitter.parseRetryAfter("  5 ", now))
        }

        @Test
        fun `should clamp delta-seconds to maxRetryAfter`() {
            assertEquals(60.seconds, noJitter.parseRetryAfter("3600", now))
            assertEquals(60.seconds, noJitter.parseRetryAfter("99999999999999999999999", now))
        }

        @Test
        fun `should respect custom maxRetryAfter`() {
            val policy = CCloudRetryPolicy(maxRetryAfter = 5.seconds)
            assertEquals(5.seconds, policy.parseRetryAfter("30", now))
        }

        @Test
        fun `should parse HTTP-date in the future`() {
            assertEquals(30.seconds, noJitter.parseRetryAfter("Wed, 21 Oct 2015 07:28:30 GMT", now))
        }

        @Test
        fun `should clamp far-future HTTP-date to maxRetryAfter`() {
            assertEquals(60.seconds, noJitter.parseRetryAfter("Thu, 22 Oct 2015 07:28:00 GMT", now))
        }

        @Test
        fun `should return zero for HTTP-date in the past`() {
            assertEquals(Duration.ZERO, noJitter.parseRetryAfter("Tue, 20 Oct 2015 07:28:00 GMT", now))
        }

        @Test
        fun `should return null for missing header`() {
            assertNull(noJitter.parseRetryAfter(null, now))
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", "-5", "1.5", "soon", "12abc", "Wed, 99 Oct 2015 07:28:00 GMT"])
        fun `should return null for invalid values`(header: String) {
            assertNull(noJitter.parseRetryAfter(header, now))
        }
    }
}
