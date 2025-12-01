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
import java.util.UUID

@TestApplication
class TelemetryUtilsTest {

    @Nested
    @DisplayName("getAnonymisedHostname")
    inner class GetAnonymisedHostnameTests {

        @Test
        fun `returns valid 16 character hex string`() {
            val deviceId = TelemetryUtils.getAnonymisedHostname()

            assertNotEquals("unknown", deviceId)
            assertEquals(16, deviceId.length)
            assertTrue(deviceId.matches(HEX_PATTERN))
        }

        @Test
        fun `returns consistent value on repeated calls`() {
            val id1 = TelemetryUtils.getAnonymisedHostname()
            val id2 = TelemetryUtils.getAnonymisedHostname()
            val id3 = TelemetryUtils.getAnonymisedHostname()

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

                val deviceId = TelemetryUtils.getAnonymisedHostname()

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

                val deviceIdWithDot = TelemetryUtils.getAnonymisedHostname()
                val deviceIdWithoutDot = TelemetryUtils.getAnonymisedHostname()

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
                    deviceIds.add(TelemetryUtils.getAnonymisedHostname())
                }

                assertEquals(deviceIds.size, deviceIds.distinct().size, "Different hostnames should produce different device IDs")
            }
        }

        @Test
        fun `returns unknown when InetAddress throws exception`() {
            mockStatic(InetAddress::class.java).use { mockedStatic ->
                mockedStatic.`when`<InetAddress> { InetAddress.getLocalHost() }
                    .thenThrow(java.net.UnknownHostException("Test exception"))

                val deviceId = TelemetryUtils.getAnonymisedHostname()

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

    @Nested
    @DisplayName("commonMachineId")
    inner class CommonMachineId {

        @Test
        fun `returns valid UUID`() {
            val machineId = TelemetryUtils.commonMachineId()

            assertNotNull(machineId)
            assertNotEquals("unknown", machineId)
            assertDoesNotThrow {
                UUID.fromString(machineId)
            }
        }

        @Test
        fun `returns same value on multiple calls`() {
            val firstCall = TelemetryUtils.commonMachineId()
            val secondCall = TelemetryUtils.commonMachineId()
            val thirdCall = TelemetryUtils.commonMachineId()

            assertEquals(firstCall, secondCall)
            assertEquals(secondCall, thirdCall)
        }

        @Test
        fun `returns properly formatted UUID`() {
            val machineId = TelemetryUtils.commonMachineId()

            val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            assertTrue(uuidPattern.matches(machineId))
        }

        @Test
        fun `handles exceptions`() {
            assertDoesNotThrow {
                TelemetryUtils.commonMachineId()
            }
        }
    }

    @Nested
    @DisplayName("getPluginVersion")
    inner class GetPluginVersion {

        @Test
        fun `returns non-empty string`() {
            val version = TelemetryUtils.getPluginVersion()

            assertNotNull(version)
            assertTrue(version.isNotEmpty())
        }

        @Test
        fun `handles exceptions`() {
            assertDoesNotThrow {
                TelemetryUtils.getPluginVersion()
            }
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

