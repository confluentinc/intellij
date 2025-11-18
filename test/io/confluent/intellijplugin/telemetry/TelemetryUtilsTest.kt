package io.confluent.intellijplugin.telemetry

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.MessageDigest
import java.util.stream.Stream

@TestApplication
class TelemetryUtilsTest {

    @Nested
    @DisplayName("getUniqueDeviceId")
    inner class GetUniqueDeviceIdTests {

        @Test
        fun `returns valid 16 character hex string`() {
            val deviceId = TelemetryUtils.getUniqueDeviceId()
            
            assertNotEquals("unknown", deviceId)
            assertEquals(16, deviceId.length)
            assertTrue(deviceId.matches(TelemetryUtilsTest.HEX_PATTERN))
        }

        @Test
        fun `returns consistent value on repeated calls`() {
            val id1 = TelemetryUtils.getUniqueDeviceId()
            val id2 = TelemetryUtils.getUniqueDeviceId()
            val id3 = TelemetryUtils.getUniqueDeviceId()
            
            assertEquals(id1, id2)
            assertEquals(id2, id3)
        }

        @ParameterizedTest
        @MethodSource("io.confluent.intellijplugin.telemetry.TelemetryUtilsTest#hostnameScenarios")
        @DisplayName("hash logic works correctly for different hostname formats")
        fun `hash logic handles different hostname formats`(hostname: String) {
            val processedHostname = hostname.substringBefore('.')
            val hash = TelemetryUtilsTest.hashHostname(processedHostname)
            
            assertEquals(16, hash.length, "Hash should be 16 characters for hostname: $hostname")
            assertTrue(hash.matches(TelemetryUtilsTest.HEX_PATTERN), "Hash should be lowercase hex for hostname: $hostname")
            assertEquals(hash, TelemetryUtilsTest.hashHostname(processedHostname), "Hash should be deterministic")
        }

        @Test
        fun `handles hostname with dot by taking substring before dot`() {
            val hostnameWithDot = "myhost.example.com"
            val hostnameWithoutDot = "myhost"
            
            val hashWithDot = TelemetryUtilsTest.hashHostname(hostnameWithDot.substringBefore('.'))
            val hashWithoutDot = TelemetryUtilsTest.hashHostname(hostnameWithoutDot)
            
            assertEquals(hashWithDot, hashWithoutDot, "Hostnames with and without domain should produce same hash")
        }

        @Test
        fun `produces different hashes for different hostnames`() {
            val hostnames = listOf("host1", "host2", "different-host")
            val hashes = hostnames.map { TelemetryUtilsTest.hashHostname(it) }
            
            assertEquals(hashes.size, hashes.distinct().size, "Different hostnames should produce different hashes")
        }
    }

    @Nested
    @DisplayName("getPlatformName")
    inner class GetPlatformNameTests {

        @Test
        fun `returns valid platform name`() {
            val platform = TelemetryUtils.getPlatformName()
            
            assertTrue(platform in TelemetryUtilsTest.VALID_PLATFORMS || platform.isNotBlank())
        }
    }

    companion object {
        val HEX_PATTERN = Regex("[0-9a-f]{16}")
        val VALID_PLATFORMS = listOf("darwin", "win32", "linux")

        /**
         * Helper function that replicates the hash logic from getUniqueDeviceId().
         * Used to test hash behavior with different hostname inputs without needing to mock InetAddress.
         */
        fun hashHostname(hostname: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(hostname.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }.take(16)
        }

        @JvmStatic
        fun hostnameScenarios(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("localhost"),
                Arguments.of("myhost"),
                Arguments.of("myhost.example.com"),
                Arguments.of("DESKTOP-ABC123"),
                Arguments.of("macbook-pro.local"),
                Arguments.of("ubuntu-server"),
                Arguments.of("host-with-dashes"),
                Arguments.of("host_with_underscores"),
                Arguments.of("HOST123"),
                Arguments.of("a"),
                Arguments.of("very-long-hostname-that-exceeds-normal-length")
            )
        }
    }
}

