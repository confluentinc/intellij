package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.DATA_MANAGER
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.data.CCloudOrgManager
import io.confluent.intellijplugin.data.KafkaDataManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@TestApplication
class KafkaCreateProducerActionTest {

    private val action = KafkaCreateProducerAction()

    private fun eventWithDataManager(dataManager: Any?) =
        TestActionEvent.createTestEvent(action) { key ->
            if (key == DATA_MANAGER.name) dataManager else null
        }

    @Nested
    @DisplayName("update")
    inner class Update {

        @Test
        fun `should be visible and enabled for KafkaDataManager`() {
            val event = eventWithDataManager(mock<KafkaDataManager>())

            action.update(event)

            assertTrue(event.presentation.isVisible)
            assertTrue(event.presentation.isEnabled)
        }

        @Test
        fun `should be visible and enabled for CCloudClusterDataManager`() {
            val event = eventWithDataManager(mock<CCloudClusterDataManager>())

            action.update(event)

            assertTrue(event.presentation.isVisible)
            assertTrue(event.presentation.isEnabled)
        }

        @Test
        fun `should be visible but disabled for CCloudOrgManager`() {
            val event = eventWithDataManager(mock<CCloudOrgManager>())

            action.update(event)

            assertTrue(event.presentation.isVisible)
            assertFalse(event.presentation.isEnabled)
        }

        @Test
        fun `should be hidden when no data manager`() {
            val event = eventWithDataManager(null)

            action.update(event)

            assertFalse(event.presentation.isVisible)
            assertFalse(event.presentation.isEnabled)
        }
    }
}
