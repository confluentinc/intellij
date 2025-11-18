package io.confluent.intellijplugin.telemetry

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.*
import org.mockito.Mockito.mockStatic
import java.net.InetAddress
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
            assertTrue(deviceId.matches(HEX_PATTERN))
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
        @DisplayName("works correctly for different hostname formats")
        fun `works correctly for different hostname formats`(hostname: String) {
            val mockInetAddress = mock(InetAddress::class.java)
            `when`(mockInetAddress.hostName).thenReturn(hostname)
            
            mockStatic(InetAddress::class.java).use { mockedStatic ->
                mockedStatic.`when`<InetAddress> { InetAddress.getLocalHost() }.thenReturn(mockInetAddress)
                
                val deviceId = TelemetryUtils.getUniqueDeviceId()
                
                assertEquals(16, deviceId.length, "Device ID should be 16 characters for hostname: $hostname")
                assertTrue(deviceId.matches(HEX_PATTERN), "Device ID should be lowercase hex for hostname: $hostname")
                assertNotEquals("unknown", deviceId, "Should not return unknown for valid hostname: $hostname")
            }
        }

        @Test
        fun `handles hostname with dot by taking substring before dot`() {
            val hostnameWithDot = "myhost.example.com"
            val hostnameWithoutDot = "myhost"
            
            val mockInetAddressWithDot = mock(InetAddress::class.java)
            `when`(mockInetAddressWithDot.hostName).thenReturn(hostnameWithDot)
            
            val mockInetAddressWithoutDot = mock(InetAddress::class.java)
            `when`(mockInetAddressWithoutDot.hostName).thenReturn(hostnameWithoutDot)
            
            mockStatic(InetAddress::class.java).use { mockedStatic ->
                mockedStatic.`when`<InetAddress> { InetAddress.getLocalHost() }
                    .thenReturn(mockInetAddressWithDot)
                    .thenReturn(mockInetAddressWithoutDot)
                
                val deviceIdWithDot = TelemetryUtils.getUniqueDeviceId()
                val deviceIdWithoutDot = TelemetryUtils.getUniqueDeviceId()
                
                assertEquals(deviceIdWithDot, deviceIdWithoutDot, "Hostnames with and without domain should produce same device ID")
            }
        }

        @Test
        fun `produces different device IDs for different hostnames`() {
            val hostnames = listOf("host1", "host2", "different-host")
            val deviceIds = mutableListOf<String>()
            
            mockStatic(InetAddress::class.java).use { mockedStatic ->
                hostnames.forEach { hostname ->
                    val mockInetAddress = mock(InetAddress::class.java)
                    `when`(mockInetAddress.hostName).thenReturn(hostname)
                    mockedStatic.`when`<InetAddress> { InetAddress.getLocalHost() }.thenReturn(mockInetAddress)
                    deviceIds.add(TelemetryUtils.getUniqueDeviceId())
                }
                
                assertEquals(deviceIds.size, deviceIds.distinct().size, "Different hostnames should produce different device IDs")
            }
        }

        @Test
        fun `returns unknown when InetAddress throws exception`() {
            mockStatic(InetAddress::class.java).use { mockedStatic ->
                mockedStatic.`when`<InetAddress> { InetAddress.getLocalHost() }
                    .thenThrow(java.net.UnknownHostException("Test exception"))
                
                val deviceId = TelemetryUtils.getUniqueDeviceId()
                
                assertEquals("unknown", deviceId)
            }
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

