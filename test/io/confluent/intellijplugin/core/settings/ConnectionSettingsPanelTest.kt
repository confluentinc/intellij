package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.MasterDetailsComponent.MyNode
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import io.confluent.intellijplugin.core.settings.connections.ConnectionConfigurable
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    @DisplayName("getDisplayName")
    inner class GetDisplayName {

        @Test
        fun `should return non-empty localized string`() {
            val panel = ConnectionSettingsPanel(mockProject)

            assertNotNull(panel.displayName)
            assertTrue(panel.displayName.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("createCurrentTestConnection")
    inner class CreateCurrentTestConnection {

        @Test
        fun `should return null when nothing selected`() {
            val panel = ConnectionSettingsPanel(mockProject)

            assertNull(panel.createCurrentTestConnection())
        }
    }

    @Nested
    @DisplayName("nodeComparator")
    inner class NodeComparator {

        private lateinit var comparator: Comparator<MyNode>

        @BeforeEach
        fun setUpComparator() {
            val panel = ConnectionSettingsPanel(mockProject)
            // getNodeComparator() is protected in MasterDetailsComponent and ConnectionSettingsPanel
            // is final, so we use reflection to access the comparator for testing sort logic.
            val method = MasterDetailsComponent::class.java.getDeclaredMethod("getNodeComparator")
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            comparator = method.invoke(panel) as Comparator<MyNode>
        }

        @Test
        fun `should sort enabled connections before disabled`() {
            val enabledNode = createConnectionNode("B Connection", isEnabled = true)
            val disabledNode = createConnectionNode("A Connection", isEnabled = false)

            assertTrue(comparator.compare(enabledNode, disabledNode) < 0)
            assertTrue(comparator.compare(disabledNode, enabledNode) > 0)
        }

        @Test
        fun `should sort alphabetically when both enabled`() {
            val nodeA = createConnectionNode("Alpha", isEnabled = true)
            val nodeB = createConnectionNode("Beta", isEnabled = true)

            assertTrue(comparator.compare(nodeA, nodeB) < 0)
            assertTrue(comparator.compare(nodeB, nodeA) > 0)
        }

        @Test
        fun `should sort alphabetically when both disabled`() {
            val nodeA = createConnectionNode("Alpha", isEnabled = false)
            val nodeB = createConnectionNode("Beta", isEnabled = false)

            assertTrue(comparator.compare(nodeA, nodeB) < 0)
        }

        @Test
        fun `should return zero for same name and enabled state`() {
            val node1 = createConnectionNode("Same", isEnabled = true)
            val node2 = createConnectionNode("Same", isEnabled = true)

            assertEquals(0, comparator.compare(node1, node2))
        }

        @Test
        fun `should sort non-connection nodes alphabetically`() {
            val nodeA = createGroupNode("Alpha")
            val nodeB = createGroupNode("Beta")

            assertTrue(comparator.compare(nodeA, nodeB) < 0)
        }

        private fun createConnectionNode(name: String, isEnabled: Boolean): MyNode {
            val mockData = mock<ConnectionData> {
                on { this.isEnabled } doReturn isEnabled
            }
            val mockConfigurable = mock<ConnectionConfigurable<*, *>> {
                on { displayName } doReturn name
                on { editableObject } doReturn mockData
            }
            return MyNode(mockConfigurable as NamedConfigurable<*>)
        }

        private fun createGroupNode(name: String): MyNode {
            val mockConfigurable = mock<NamedConfigurable<*>> {
                on { displayName } doReturn name
            }
            return MyNode(mockConfigurable)
        }
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
