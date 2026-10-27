package io.confluent.intellijplugin.ccloud.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TokenBucketRateLimiterTest {

    private class FakeClock(var nanos: Long = 1_000_000_000L) {
        fun advanceMillis(ms: Long) {
            nanos += ms * 1_000_000L
        }
    }

    @Nested
    inner class Refill {

        @Test
        fun `should start full`() = runBlocking {
            val clock = FakeClock()
            val limiter = TokenBucketRateLimiter(10) { clock.nanos }

            assertEquals(10.0, limiter.availableTokens(), 1e-9)
        }

        @Test
        fun `should consume one token per acquire without waiting while tokens remain`() = runBlocking {
            val clock = FakeClock()
            val limiter = TokenBucketRateLimiter(10) { clock.nanos }

            repeat(4) { limiter.acquire() }

            assertEquals(6.0, limiter.availableTokens(), 1e-9)
        }

        @Test
        fun `should refill proportionally to monotonic elapsed time`() = runBlocking {
            val clock = FakeClock()
            val limiter = TokenBucketRateLimiter(10) { clock.nanos }
            repeat(10) { limiter.acquire() }

            clock.advanceMillis(250)

            assertEquals(2.5, limiter.availableTokens(), 1e-9)
        }

        @Test
        fun `should not exceed capacity after long idle`() = runBlocking {
            val clock = FakeClock()
            val limiter = TokenBucketRateLimiter(10) { clock.nanos }
            limiter.acquire()

            clock.advanceMillis(60_000)

            assertEquals(10.0, limiter.availableTokens(), 1e-9)
        }

        @Test
        fun `should ignore a clock that goes backwards`() = runBlocking {
            val clock = FakeClock()
            val limiter = TokenBucketRateLimiter(10) { clock.nanos }
            repeat(5) { limiter.acquire() }

            clock.nanos -= 5_000_000_000L

            assertEquals(5.0, limiter.availableTokens(), 1e-9)
        }
    }
}
