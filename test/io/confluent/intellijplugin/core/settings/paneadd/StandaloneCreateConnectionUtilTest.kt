package io.confluent.intellijplugin.core.settings.paneadd

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.settings.connections.BrokerConnectionGroup
import io.confluent.intellijplugin.core.settings.connections.CCloudDisplayGroup
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.core.settings.connections.ConnectionGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@TestApplication
class StandaloneCreateConnectionUtilTest {

    @Nested
    @DisplayName("groupsPriority")
    inner class GroupsPriority {

        @Test
        fun `should assign CCloud higher priority than Broker`() {
            val ccloudPriority = StandaloneCreateConnectionUtil.groupsPriority[CCloudDisplayGroup.GROUP_ID]!!
            val brokerPriority = StandaloneCreateConnectionUtil.groupsPriority[BrokerConnectionGroup.GROUP_ID]!!

            assertTrue(ccloudPriority < brokerPriority, "CCloud (${ccloudPriority}) should have lower number (higher priority) than Broker (${brokerPriority})")
        }
    }

    @Nested
    @DisplayName("linearizeActions")
    inner class LinearizeActions {

        @Test
        fun `should linearize flat action group with separator`() {
            val action1 = mock<AnAction>()
            val action2 = mock<AnAction>()
            val group = DefaultActionGroup("Root", true).apply {
                add(action1)
                add(action2)
            }

            val result = StandaloneCreateConnectionUtil.linearizeActions(group)

            // First element is a separator (from the root group), then the actions
            assertTrue(result[0] is Separator, "First element should be a Separator")
            assertEquals(action1, result[1])
            assertEquals(action2, result[2])
        }

        @Test
        fun `should linearize nested groups into flat list`() {
            val leafAction = mock<AnAction>()
            val innerGroup = DefaultActionGroup("Inner", true).apply {
                add(leafAction)
            }
            val rootGroup = DefaultActionGroup("Root", true).apply {
                add(innerGroup)
            }

            val result = StandaloneCreateConnectionUtil.linearizeActions(rootGroup)

            val actions = result.filter { it !is Separator }

            assertEquals(1, actions.size, "Should contain exactly one non-separator action")
            assertEquals(leafAction, actions[0])
        }
    }

    @Nested
    @DisplayName("createRootAddAction")
    inner class CreateRootAddAction {

        @Test
        fun `should handle parent-child group hierarchy`() {
            val project = mock<Project>()
            val parentGroup = mock<ConnectionGroup> {
                on { id } doReturn "parent"
                on { name } doReturn "Parent"
                on { parentGroupId } doReturn null
                on { visible } doReturn true
            }
            val childFactory = mock<ConnectionFactory<*>> {
                on { id } doReturn "child"
                on { name } doReturn "Child"
                on { parentGroupId } doReturn "parent"
                on { visible } doReturn true
            }

            val groups = listOf(parentGroup, childFactory)
            val rootAction = StandaloneCreateConnectionUtil.createRootAddAction(project, groups)

            val children = rootAction.getChildren(null)
            assertTrue(children.isNotEmpty(), "Should have at least one top-level group")
        }

        @Test
        fun `should handle empty groups list`() {
            val project = mock<Project>()

            val rootAction = StandaloneCreateConnectionUtil.createRootAddAction(project, emptyList())

            val children = rootAction.getChildren(null)
            assertEquals(0, children.size)
        }
    }
}
