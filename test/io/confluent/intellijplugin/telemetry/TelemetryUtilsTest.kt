package io.confluent.intellijplugin.telemetry

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

@TestApplication
class TelemetryUtilsTest {

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
}
