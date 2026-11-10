package io.confluent.intellijplugin.ccloud.client

import java.time.Instant
import java.time.Duration as JavaDuration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import org.jetbrains.annotations.VisibleForTesting

/**
 * Retry timing policy shared by Confluent Cloud REST callers.
 *
 * Computes how long to wait before each retry using capped exponential backoff with one-sided
 * jitter, and never waits less than the server's `Retry-After`. Deciding *whether* a failure is
 * retryable is the caller's job; this class only answers *how many* attempts and *how long* to wait.
 *
 * There is intentionally no total delay budget: the only caps are per-attempt ([maxBackoff] for
 * computed backoff, [maxRetryAfter] for server-supplied delays). A server delay above
 * [maxRetryAfter] means the caller should give up, not retry early (see [exceedsMaxRetryAfter]).
 *
 * @param maxAttempts Total attempts including the first one (so `maxAttempts - 1` retries)
 * @param initialBackoff Backoff before the first retry, before jitter
 * @param multiplier Growth factor applied to the backoff for each subsequent retry
 * @param maxBackoff Upper bound for the computed (exponential) backoff, before jitter
 * @param jitter Maximum extra fraction added to the delay; actual factor is uniform in `[1, 1 + jitter)`
 * @param maxRetryAfter Longest server-supplied `Retry-After` worth waiting for before a retry
 * @param random Source of randomness for jitter (injectable for tests)
 */
data class CCloudRetryPolicy(
    val maxAttempts: Int = 4,
    val initialBackoff: Duration = 1.seconds,
    val multiplier: Double = 1.6,
    val maxBackoff: Duration = 10.seconds,
    val jitter: Double = 0.2,
    val maxRetryAfter: Duration = 60.seconds,
    val random: Random = Random.Default
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        require(!initialBackoff.isNegative()) { "initialBackoff must be >= 0, was $initialBackoff" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0, was $multiplier" }
        require(maxBackoff >= initialBackoff) { "maxBackoff ($maxBackoff) must be >= initialBackoff ($initialBackoff)" }
        require(jitter.isFinite() && jitter >= 0.0) { "jitter must be finite and >= 0, was $jitter" }
        require(!maxRetryAfter.isNegative()) { "maxRetryAfter must be >= 0, was $maxRetryAfter" }
    }

    /**
     * Delay to wait before retry number [retryIndex] (0 = the wait before the first retry).
     *
     * `max(min(initialBackoff * multiplier^retryIndex, maxBackoff), retryAfter) * (1 + U[0, jitter))`
     *
     * Jitter only ever adds time, so the result is never below [retryAfter] (after clamping it to
     * [maxRetryAfter]). The clamp is a safety net: callers should check [exceedsMaxRetryAfter]
     * first and stop retrying instead.
     */
    fun delayFor(retryIndex: Int, retryAfter: Duration? = null): Duration {
        require(retryIndex >= 0) { "retryIndex must be >= 0, was $retryIndex" }

        // Zero backoff short-circuits: 0 * multiplier^n is NaN once the power overflows to Infinity.
        val backoff = if (initialBackoff == Duration.ZERO) {
            Duration.ZERO
        } else {
            val maxBackoffNanos = maxBackoff.inWholeNanoseconds.toDouble()
            (initialBackoff.inWholeNanoseconds * multiplier.pow(retryIndex))
                .coerceAtMost(maxBackoffNanos)
                .roundToLong()
                .nanoseconds
        }

        val serverDelay = retryAfter?.coerceIn(Duration.ZERO, maxRetryAfter) ?: Duration.ZERO
        return jittered(maxOf(backoff, serverDelay))
    }

    /**
     * Apply this policy's one-sided jitter to [base]: `base * (1 + U[0, jitter))`.
     * Never returns less than [base]. Useful for callers that pace their own waits (e.g. poll loops).
     */
    fun jittered(base: Duration): Duration {
        require(!base.isNegative()) { "base must be >= 0, was $base" }
        val factor = 1.0 + random.nextDouble() * jitter
        return base * factor
    }

    /**
     * Parse a `Retry-After` header value (RFC 9110 §10.2.3).
     *
     * Accepts delta-seconds (`"120"`) or an IMF-fixdate HTTP-date
     * (`"Wed, 21 Oct 2015 07:28:00 GMT"`). The server's value is returned as is, not capped at
     * [maxRetryAfter], so callers can tell when it asked for more (see [exceedsMaxRetryAfter]). An
     * HTTP-date in the past yields [Duration.ZERO]; delta-seconds too large for a Long yield
     * [Duration.INFINITE].
     *
     * @return the delay, or `null` if the header is absent, blank, negative or unparseable
     */
    fun parseRetryAfter(header: String?, now: Instant = Instant.now()): Duration? {
        val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        if (value.all { it in '0'..'9' }) {
            // Digits only, so a null here means overflow: the server asked for a very long wait.
            return value.toLongOrNull()?.seconds ?: Duration.INFINITE
        }

        val date = try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            return null
        }

        return JavaDuration.between(now, date).toKotlinDuration().coerceAtLeast(Duration.ZERO)
    }

    /**
     * Whether the server asked to wait longer than [maxRetryAfter]. If so, the caller should stop
     * retrying and surface the error: retrying sooner than the server asked would most likely just
     * get the same error again.
     */
    fun exceedsMaxRetryAfter(retryAfter: Duration?): Boolean = retryAfter != null && retryAfter > maxRetryAfter

    companion object {
        val DEFAULT = CCloudRetryPolicy()

        /** Header name for the server-requested retry delay. */
        const val RETRY_AFTER_HEADER = "Retry-After"

        /**
         * Near-instant policy (1ms waits, no jitter) intended for tests. Its 1ms [maxRetryAfter]
         * means any non-zero `Retry-After` exceeds it, so tests that need to retry past a
         * `Retry-After` should raise [maxRetryAfter].
         */
        @VisibleForTesting
        internal fun immediate(maxAttempts: Int = 4) = CCloudRetryPolicy(
            maxAttempts = maxAttempts,
            initialBackoff = 1.milliseconds,
            maxBackoff = 1.milliseconds,
            jitter = 0.0,
            maxRetryAfter = 1.milliseconds
        )
    }
}
