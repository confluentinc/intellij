package io.confluent.intellijplugin.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

@DisplayName("TimeUtils")
class TimeUtilsTest {

    @Nested
    @DisplayName("unixTimeToString")
    inner class UnixTimeToString {

        @Test
        fun `should format epoch timestamp using current timezone`() {
            val unixtime = 1700000000000L
            val expected = Instant.ofEpochMilli(unixtime)
                .atZone(TimeZone.getDefault().toZoneId())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            assertEquals(expected, TimeUtils.unixTimeToString(unixtime))
        }

        @Test
        fun `should format epoch zero in UTC consistently`() {
            // Build expectation in the JVM's default zone — independent of the test machine
            val expected = Instant.ofEpochMilli(0L)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            assertEquals(expected, TimeUtils.unixTimeToString(0L))
        }
    }

    @Nested
    @DisplayName("intervalAsString")
    inner class IntervalAsString {

        @Test
        fun `should return less-than-one-ms placeholder for zero`() {
            assertEquals("<1 ms", TimeUtils.intervalAsString(0L))
        }

        @Test
        fun `should format only milliseconds when below one second`() {
            assertEquals("500 ms", TimeUtils.intervalAsString(500L))
        }

        @Test
        fun `should omit ms when withMs is false`() {
            assertEquals("", TimeUtils.intervalAsString(500L, withMs = false))
        }

        @Test
        fun `should format seconds and milliseconds`() {
            assertEquals("1 s 500 ms", TimeUtils.intervalAsString(1500L))
        }

        @Test
        fun `should format whole seconds without ms`() {
            assertEquals("3 s", TimeUtils.intervalAsString(3000L))
        }

        @Test
        fun `should format minutes and seconds`() {
            assertEquals("2 m 5 s", TimeUtils.intervalAsString(2 * 60_000L + 5_000L))
        }

        @Test
        fun `should format hours, minutes and seconds`() {
            val ms = 3_600_000L + 2 * 60_000L + 5_000L
            assertEquals("1 h 2 m 5 s", TimeUtils.intervalAsString(ms))
        }

        @Test
        fun `should format days, hours and minutes when days are non-zero`() {
            val ms = 2 * 86_400_000L + 3 * 3_600_000L + 4 * 60_000L
            assertEquals("2 d 3 h 4 m", TimeUtils.intervalAsString(ms))
        }

        @Test
        fun `should drop seconds and ms when days are non-zero`() {
            // Even though we pass seconds and ms, the day-branch only renders d/h/m
            val ms = 1 * 86_400_000L + 30_000L + 250L
            assertEquals("1 d", TimeUtils.intervalAsString(ms))
        }

        @Test
        fun `should suppress ms when withMs is false but render seconds`() {
            assertEquals("1 s", TimeUtils.intervalAsString(1500L, withMs = false))
        }

        @Test
        fun `should render only minutes when seconds and ms are zero`() {
            assertEquals("5 m", TimeUtils.intervalAsString(5 * 60_000L))
        }
    }

    @Nested
    @DisplayName("parseIsoTime")
    inner class ParseIsoTime {

        @Test
        fun `should return null for null input`() {
            assertNull(TimeUtils.parseIsoTime(null))
        }

        @Test
        fun `should return null for invalid input`() {
            assertNull(TimeUtils.parseIsoTime("not-a-timestamp"))
        }

        @Test
        fun `should return null for empty string`() {
            assertNull(TimeUtils.parseIsoTime(""))
        }

        @Test
        fun `should parse valid ISO_INSTANT timestamp`() {
            val result = TimeUtils.parseIsoTime("2023-11-14T22:13:20Z")
            assertNotNull(result)
            assertEquals(1700000000000L, result!!.time)
        }

        @Test
        fun `should return null for non-ISO formats`() {
            // ISO_INSTANT requires the trailing Z and a specific shape
            assertNull(TimeUtils.parseIsoTime("2023-11-14 22:13:20"))
        }
    }
}
