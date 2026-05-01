package io.confluent.intellijplugin.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowController
import io.confluent.intellijplugin.scaffold.actions.SelectScaffoldTemplateAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class KafkaMonitoringToolWindowControllerTest {

    private val project = ProjectManager.getInstance().defaultProject

    private fun invokeExtraTitleActions(controller: MonitoringToolWindowController): List<AnAction> {
        val method = MonitoringToolWindowController::class.java
            .getDeclaredMethod("extraTitleActions")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(controller) as List<AnAction>
    }

    @Nested
    @DisplayName("companion constants")
    inner class CompanionConstants {

        @Test
        fun `SCAFFOLD_ACTION_ID matches plugin xml registration`() {
            assertEquals(
                "Kafka.SelectScaffoldTemplate",
                KafkaMonitoringToolWindowController.SCAFFOLD_ACTION_ID
            )
        }

        @Test
        fun `TOOL_WINDOW_ID is KafkaToolWindow`() {
            assertEquals("KafkaToolWindow", KafkaMonitoringToolWindowController.TOOL_WINDOW_ID)
        }
    }

    @Nested
    @DisplayName("extraTitleActions")
    inner class ExtraTitleActions {

        @Test
        fun `returns the registered scaffold action`() {
            val controller = KafkaMonitoringToolWindowController.getInstance(project)
            assertNotNull(controller, "Project service should be available")

            val actions = invokeExtraTitleActions(controller!!)

            assertEquals(1, actions.size)
            assertTrue(
                actions[0] is SelectScaffoldTemplateAction,
                "First extra title action should be the scaffold template action"
            )
        }
    }
}
