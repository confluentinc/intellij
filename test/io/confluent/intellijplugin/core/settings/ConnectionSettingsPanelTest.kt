package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@TestApplication
class ConnectionSettingsPanelTest {

    private val disposable = Disposer.newDisposable("ConnectionSettingsPanelTest")
    private lateinit var mockProject: Project
    private lateinit var mockDataManager: RfsConnectionDataManager

    @BeforeEach
    fun setUp() {
        mockProject = mock()
        mockDataManager = mock {
            on { getConnections(mockProject) } doReturn emptyList()
        }
        ApplicationManager.getApplication()
            .replaceService(RfsConnectionDataManager::class.java, mockDataManager, disposable)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    @Nested
    @DisplayName("isModified")
    inner class IsModified {

        @Test
        fun `should return false when no connections added or removed`() {
            val panel = ConnectionSettingsPanel(mockProject)

            assertFalse(panel.isModified(mockDataManager))
        }

        @Test
        fun `should return true when connection was added`() {
            val panel = ConnectionSettingsPanel(mockProject)
            val mockConnection = mock<ConnectionData>()
            panel.addedConnections.add(mockConnection)

            assertTrue(panel.isModified(mockDataManager))
        }

        @Test
        fun `should return true when connection was removed`() {
            val panel = ConnectionSettingsPanel(mockProject)
            val mockConnection = mock<ConnectionData>()
            panel.removedConnections.add(mockConnection)

            assertTrue(panel.isModified(mockDataManager))
        }
    }

}
