package io.confluent.intellijplugin.core.settings.connections

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.settings.KafkaConnectionGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
class ConnectionGroupTest {

    @Nested
    @DisplayName("init")
    inner class Init {

        @Test
        fun `should reject invisible group without parent`() {
            assertThrows<IllegalArgumentException> {
                object : ConnectionGroup(id = "bad", name = "Bad", visible = false, parentGroupId = null) {}
            }
        }
    }

    @Nested
    @DisplayName("createOptionsPanel")
    inner class CreateOptionsPanel {

        @Test
        fun `should return panel with no children by default`() {
            val group = BrokerConnectionGroup()

            val panel = group.createOptionsPanel() as javax.swing.JPanel

            assertEquals(0, panel.componentCount)
        }
    }

    @Nested
    @DisplayName("disposeOptionsPanel")
    inner class DisposeOptionsPanel {

        @Test
        fun `should not throw when called after createOptionsPanel`() {
            val group = BrokerConnectionGroup()
            group.createOptionsPanel()
            group.disposeOptionsPanel()
        }
    }

    @Nested
    @DisplayName("createBlankData")
    inner class CreateBlankData {

        private val factory = KafkaConnectionGroup()

        @Test
        fun `should assign groupId matching the factory`() {
            val data = factory.createBlankData()

            assertEquals(factory.id, data.groupId)
        }

        @Test
        fun `should generate unique IDs on successive calls`() {
            val data1 = factory.createBlankData()
            val data2 = factory.createBlankData()

            assertNotEquals(data1.innerId, data2.innerId)
        }

        @Test
        fun `should use forcedId when provided`() {
            val data = factory.createBlankData(forcedId = "custom-id-123")

            assertEquals("custom-id-123", data.innerId)
        }

        @Test
        fun `should set perProject flag when requested`() {
            val data = factory.createBlankData(perProject = true)

            assertTrue(data.isPerProject)
        }
    }
}
