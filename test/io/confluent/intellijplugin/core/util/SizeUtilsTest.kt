package io.confluent.intellijplugin.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.text.DecimalFormat

@DisplayName("SizeUtils")
class SizeUtilsTest {

    private val decimalFormat = DecimalFormat("0.##")

    private fun expectedSize(value: Double, unit: String) = "${decimalFormat.format(value)} $unit"

    @Nested
    @DisplayName("toString(Long)")
    inner class ToStringLong {

        @Test
        fun `should format zero as 0 B`() {
            assertEquals(expectedSize(0.0, "B"), SizeUtils.toString(0L))
        }

        @Test
        fun `should format bytes below 1 KB`() {
            assertEquals(expectedSize(512.0, "B"), SizeUtils.toString(512L))
        }

        @Test
        fun `should format exactly 1 KB`() {
            assertEquals(expectedSize(1.0, "KB"), SizeUtils.toString(1024L))
        }

        @Test
        fun `should format kilobytes with fractional part`() {
            assertEquals(expectedSize(1.5, "KB"), SizeUtils.toString(1536L))
        }

        @Test
        fun `should format exactly 1 MB`() {
            assertEquals(expectedSize(1.0, "MB"), SizeUtils.toString(1024L * 1024))
        }

        @Test
        fun `should format exactly 1 GB`() {
            assertEquals(expectedSize(1.0, "GB"), SizeUtils.toString(1024L * 1024 * 1024))
        }

        @Test
        fun `should format exactly 1 TB`() {
            assertEquals(expectedSize(1.0, "TB"), SizeUtils.toString(1024L * 1024 * 1024 * 1024))
        }

        @Test
        fun `should not exceed TB unit for very large values`() {
            // 1024 TB stays in TB (no PB unit defined)
            val result = SizeUtils.toString(1024L * 1024 * 1024 * 1024 * 1024)
            assertEquals(expectedSize(1024.0, "TB"), result)
        }

        @Test
        fun `should round to two decimal places`() {
            // 1234 bytes = 1.205078125 KB -> rounds to 1.21 KB
            assertEquals(expectedSize(1.21, "KB"), SizeUtils.toString(1234L))
        }

        @Test
        fun `should handle just below 1 KB`() {
            assertEquals(expectedSize(1023.0, "B"), SizeUtils.toString(1023L))
        }
    }

    @Nested
    @DisplayName("toString(Int)")
    inner class ToStringInt {

        @Test
        fun `should delegate to long overload`() {
            assertEquals(expectedSize(1.0, "KB"), SizeUtils.toString(1024))
        }

        @Test
        fun `should format zero`() {
            assertEquals(expectedSize(0.0, "B"), SizeUtils.toString(0))
        }

        @Test
        fun `should format small int values as bytes`() {
            assertEquals(expectedSize(100.0, "B"), SizeUtils.toString(100))
        }
    }
}
