package io.confluent.intellijplugin.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import io.confluent.intellijplugin.toolwindow.KafkaMonitoringToolWindowController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@TestApplication
class MonitoringTabDeleteConnectionTest {

    private val kafkaConnectionId = "550e8400-e29b-41d4-a716-446655440000"

    private val disposable = Disposer.newDisposable("MonitoringTabDeleteConnectionTest")
    private val project = ProjectManager.getInstance().defaultProject
    private val action = MonitoringTabDeleteConnection()

    private lateinit var mockController: KafkaMonitoringToolWindowController

    @BeforeEach
    fun setUp() {
        mockController = mock()
        project.replaceService(KafkaMonitoringToolWindowController::class.java, mockController, disposable)

        // Force the propagation loop to hit exactly our one test project.
        val mockProjectManager = mock<ProjectManager> {
            on { openProjects } doReturn arrayOf(project)
        }
        ApplicationManager.getApplication()
            .replaceService(ProjectManager::class.java, mockProjectManager, disposable)

        // Fresh settings per test so state doesn't leak across the suite.
        ApplicationManager.getApplication()
            .replaceService(KafkaPluginSettings::class.java, KafkaPluginSettings(), disposable)
    }

    @AfterEach
    fun tearDown() {
        TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        Disposer.dispose(disposable)
    }

    private fun event(connectionId: String?) = TestActionEvent.createTestEvent(action) { key ->
        when (key) {
            ConnectionUtil.CONNECTION_ID.name -> connectionId
            CommonDataKeys.PROJECT.name -> project
            else -> null
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {

        @Test
        fun `should show Hide label and enable action for ccloud tab`() {
            val event = event("ccloud")

            action.update(event)

            assertEquals("Hide", event.presentation.text)
            assertTrue(event.presentation.isEnabled)
            assertTrue(event.presentation.isVisible)
        }

        @Test
        fun `should show Delete Connection label and enable action for a connection tab`() {
            val event = event(kafkaConnectionId)

            action.update(event)

            assertTrue(event.presentation.text.startsWith("Delete Connection"))
            assertTrue(event.presentation.isEnabled)
        }

        @Test
        fun `should disable action when no connection is selected`() {
            val event = event(null)

            action.update(event)

            assertFalse(event.presentation.isEnabled)
        }
    }

    @Nested
    @DisplayName("actionPerformed on ccloud tab")
    inner class CCloudActionPerformed {

        @Test
        fun `should flip setting and remove tab across open projects when user confirms`() {
            TestDialogManager.setTestDialog { Messages.OK }

            action.actionPerformed(event("ccloud"))

            assertTrue(KafkaPluginSettings.getInstance().hideConfluentCloudTab)
            verify(mockController).removeConfluentCloudTab()
        }

        @Test
        fun `should not change setting or remove tab when user cancels`() {
            TestDialogManager.setTestDialog { Messages.CANCEL }

            action.actionPerformed(event("ccloud"))

            assertFalse(KafkaPluginSettings.getInstance().hideConfluentCloudTab)
            verify(mockController, never()).removeConfluentCloudTab()
        }
    }
}
