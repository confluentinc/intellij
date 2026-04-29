package io.confluent.intellijplugin.settings.app

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
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
import javax.swing.JCheckBox

@TestApplication
class KafkaPluginSettingsConfigurableTest {

    private val disposable = Disposer.newDisposable("KafkaPluginSettingsConfigurableTest")
    private val project = ProjectManager.getInstance().defaultProject

    private lateinit var mockController: KafkaMonitoringToolWindowController

    @BeforeEach
    fun setUp() {
        // Fresh settings per test so state doesn't leak across the suite.
        ApplicationManager.getApplication()
            .replaceService(KafkaPluginSettings::class.java, KafkaPluginSettings(), disposable)

        mockController = mock()
        project.replaceService(KafkaMonitoringToolWindowController::class.java, mockController, disposable)

        // Force apply()'s propagation loop to hit exactly our one test project.
        val mockProjectManager = mock<ProjectManager> {
            on { openProjects } doReturn arrayOf(project)
        }
        ApplicationManager.getApplication()
            .replaceService(ProjectManager::class.java, mockProjectManager, disposable)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    private fun hideCheckbox(configurable: KafkaPluginSettingsConfigurable): JCheckBox {
        val component = configurable.createComponent()
        return UIUtil.findComponentsOfType(component, JCheckBox::class.java)
            .first { it.text == "Hide Confluent Cloud connection tab" }
    }

    @Nested
    @DisplayName("metadata")
    inner class Metadata {

        @Test
        fun `should return a stable id`() {
            assertEquals("kafka_plugin_settings", KafkaPluginSettingsConfigurable().id)
        }
    }

    @Nested
    @DisplayName("createComponent")
    inner class CreateComponent {

        @Test
        fun `should expose hide-ccloud-tab checkbox bound to settings`() {
            KafkaPluginSettings.getInstance().hideConfluentCloudTab = true

            val configurable = KafkaPluginSettingsConfigurable()
            val checkbox = hideCheckbox(configurable)

            assertTrue(checkbox.isSelected)
        }
    }

    @Nested
    @DisplayName("isModified")
    inner class IsModified {

        @Test
        fun `should be true after toggling the checkbox`() {
            val configurable = KafkaPluginSettingsConfigurable()
            val checkbox = hideCheckbox(configurable)

            checkbox.isSelected = !checkbox.isSelected

            assertTrue(configurable.isModified)
        }
    }

    @Nested
    @DisplayName("apply")
    inner class Apply {

        @Test
        fun `should propagate hide=true to open projects and remove the ccloud tab`() {
            KafkaPluginSettings.getInstance().hideConfluentCloudTab = false

            val configurable = KafkaPluginSettingsConfigurable()
            val checkbox = hideCheckbox(configurable)
            checkbox.isSelected = true

            configurable.apply()

            assertTrue(KafkaPluginSettings.getInstance().hideConfluentCloudTab)
            verify(mockController).removeConfluentCloudTab()
            verify(mockController, never()).addConfluentCloudTab()
        }

        @Test
        fun `should propagate hide=false to open projects and re-add the ccloud tab`() {
            KafkaPluginSettings.getInstance().hideConfluentCloudTab = true

            val configurable = KafkaPluginSettingsConfigurable()
            val checkbox = hideCheckbox(configurable)
            checkbox.isSelected = false

            configurable.apply()

            assertFalse(KafkaPluginSettings.getInstance().hideConfluentCloudTab)
            verify(mockController).addConfluentCloudTab()
            verify(mockController, never()).removeConfluentCloudTab()
        }

        @Test
        fun `should not touch any controller when the hide flag did not change`() {
            KafkaPluginSettings.getInstance().hideConfluentCloudTab = false

            val configurable = KafkaPluginSettingsConfigurable()
            configurable.createComponent()

            configurable.apply()

            verify(mockController, never()).addConfluentCloudTab()
            verify(mockController, never()).removeConfluentCloudTab()
        }
    }

    @Nested
    @DisplayName("reset")
    inner class Reset {

        @Test
        fun `should pull current setting back into the panel`() {
            KafkaPluginSettings.getInstance().hideConfluentCloudTab = false

            val configurable = KafkaPluginSettingsConfigurable()
            val checkbox = hideCheckbox(configurable)
            checkbox.isSelected = true
            assertTrue(configurable.isModified)

            configurable.reset()

            assertFalse(checkbox.isSelected)
            assertFalse(configurable.isModified)
        }
    }
}
