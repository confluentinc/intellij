package io.confluent.intellijplugin.core.util

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
@DisplayName("BdtUrlUtils")
class BdtUrlUtilsTest {

    @Nested
    @DisplayName("getProtocol")
    inner class GetProtocol {

        @Test
        fun `should extract https protocol`() {
            assertEquals("https", BdtUrlUtils.getProtocol("https://example.com"))
        }

        @Test
        fun `should extract http protocol`() {
            assertEquals("http", BdtUrlUtils.getProtocol("http://example.com"))
        }

        @Test
        fun `should return empty string when no scheme separator is present`() {
            assertEquals("", BdtUrlUtils.getProtocol("example.com"))
        }

        @Test
        fun `should return empty string for empty input`() {
            assertEquals("", BdtUrlUtils.getProtocol(""))
        }

        @Test
        fun `should extract custom protocols`() {
            assertEquals("ftp", BdtUrlUtils.getProtocol("ftp://files.example.com"))
        }
    }

    @Nested
    @DisplayName("validateUrl")
    inner class ValidateUrl {

        @Test
        fun `should return null for a valid http URL`() {
            assertNull(BdtUrlUtils.validateUrl("http://example.com"))
        }

        @Test
        fun `should return null for a valid https URL`() {
            assertNull(BdtUrlUtils.validateUrl("https://example.com:8443/path"))
        }

        @Test
        fun `should return null for bare host (http prefix gets added)`() {
            assertNull(BdtUrlUtils.validateUrl("example.com"))
        }

        @Test
        fun `should return throwable for unknown protocol`() {
            // No URL stream handler is registered for "wsx" → URL constructor throws
            assertNotNull(BdtUrlUtils.validateUrl("wsx://example.com"))
        }
    }

    @Nested
    @DisplayName("convertToUrlObject")
    inner class ConvertToUrlObject {

        @Test
        fun `should default https URL to port 443`() {
            val url = BdtUrlUtils.convertToUrlObject("https://example.com")
            assertEquals("https", url.protocol)
            assertEquals("example.com", url.host)
            assertEquals(443, url.port)
        }

        @Test
        fun `should default http URL to port 80`() {
            val url = BdtUrlUtils.convertToUrlObject("http://example.com")
            assertEquals("http", url.protocol)
            assertEquals("example.com", url.host)
            assertEquals(80, url.port)
        }

        @Test
        fun `should preserve explicit port`() {
            val url = BdtUrlUtils.convertToUrlObject("https://example.com:9092")
            assertEquals(9092, url.port)
        }

        @Test
        fun `should add http prefix to bare host`() {
            val url = BdtUrlUtils.convertToUrlObject("example.com")
            assertEquals("http", url.protocol)
            assertEquals("example.com", url.host)
            assertEquals(80, url.port)
        }

        @Test
        fun `should trim leading and trailing whitespace`() {
            val url = BdtUrlUtils.convertToUrlObject("   http://example.com   ")
            assertEquals("example.com", url.host)
        }

        @Test
        fun `should strip trailing slash`() {
            val url = BdtUrlUtils.convertToUrlObject("http://example.com/")
            assertEquals("", url.file)
        }

        @Test
        fun `should preserve path segments other than the trailing slash`() {
            val url = BdtUrlUtils.convertToUrlObject("http://example.com/api/v1")
            assertEquals("/api/v1", url.path)
        }
    }
}
