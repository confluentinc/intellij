package io.confluent.intellijplugin.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SizeUtils")
class SizeUtilsTest {

    @Nested
    @DisplayName("toString(Long)")
    inner class ToStringLong {

        @Test
        fun `should format zero as 0 B`() {
            assertEquals("0 B", SizeUtils.toString(0L))
        }

        @Test
        fun `should format bytes below 1 KB`() {
            assertEquals("512 B", SizeUtils.toString(512L))
        }

        @Test
        fun `should format exactly 1 KB`() {
            assertEquals("1 KB", SizeUtils.toString(1024L))
        }

        @Test
        fun `should format kilobytes with fractional part`() {
            assertEquals("1.5 KB", SizeUtils.toString(1536L))
        }

        @Test
        fun `should format exactly 1 MB`() {
            assertEquals("1 MB", SizeUtils.toString(1024L * 1024))
        }

        @Test
        fun `should format exactly 1 GB`() {
            assertEquals("1 GB", SizeUtils.toString(1024L * 1024 * 1024))
        }

        @Test
        fun `should format exactly 1 TB`() {
            assertEquals("1 TB", SizeUtils.toString(1024L * 1024 * 1024 * 1024))
        }

        @Test
        fun `should not exceed TB unit for very large values`() {
            // 1024 TB stays in TB (no PB unit defined)
            val result = SizeUtils.toString(1024L * 1024 * 1024 * 1024 * 1024)
            assertEquals("1024 TB", result)
        }

        @Test
        fun `should round to two decimal places`() {
            // 1234 bytes = 1.205078125 KB -> "1.21 KB"
            assertEquals("1.21 KB", SizeUtils.toString(1234L))
        }

        @Test
        fun `should handle just below 1 KB`() {
            assertEquals("1023 B", SizeUtils.toString(1023L))
        }
    }

    @Nested
    @DisplayName("toString(Int)")
    inner class ToStringInt {

        @Test
        fun `should delegate to long overload`() {
            assertEquals("1 KB", SizeUtils.toString(1024))
        }

        @Test
        fun `should format zero`() {
            assertEquals("0 B", SizeUtils.toString(0))
        }

        @Test
        fun `should format small int values as bytes`() {
            assertEquals("100 B", SizeUtils.toString(100))
        }
    }
}
