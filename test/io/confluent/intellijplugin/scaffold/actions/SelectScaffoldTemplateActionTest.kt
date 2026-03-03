package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListMetadata
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateList
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock

@TestApplication
class SelectScaffoldTemplateActionTest {

    private fun createTemplateList(
        templates: Set<ScaffoldV1TemplateListDataInner> = emptySet()
    ): Scaffoldv1TemplateList {
        return Scaffoldv1TemplateList(
            apiVersion = Scaffoldv1TemplateList.ApiVersion.scaffoldSlashV1,
            kind = Scaffoldv1TemplateList.Kind.TemplateList,
            metadata = ScaffoldV1TemplateListMetadata(),
            data = templates
        )
    }

    private fun createTemplate(
        name: String = "test-template",
        displayName: String = "Test Template",
        description: String = "A test template"
    ): ScaffoldV1TemplateListDataInner {
        return ScaffoldV1TemplateListDataInner(
            metadata = ScaffoldV1TemplateMetadata(self = null),
            spec = Scaffoldv1TemplateSpec(
                name = name,
                displayName = displayName,
                description = description,
                version = "1.0.0",
                language = "Java",
                tags = listOf("kafka", "test")
            )
        )
    }

    @Nested
    @DisplayName("action registration")
    inner class ActionRegistration {

        @Test
        fun `action is registered in ActionManager`() {
            val action = ActionManager.getInstance().getAction("Kafka.SelectScaffoldTemplate")
            assertNotNull(action)
            assertTrue(action is SelectScaffoldTemplateAction)
        }
    }

    @Nested
    @DisplayName("actionPerformed")
    inner class ActionPerformed {

        @Test
        fun `action does nothing when project is null`() {
            val action = SelectScaffoldTemplateAction()
            val event = AnActionEvent.createFromDataContext(
                "test",
                Presentation(),
                DataContext.EMPTY_CONTEXT
            )
            // Should not throw - gracefully handles null project
            action.actionPerformed(event)
        }
    }

    @Nested
    @DisplayName("fetchTemplates")
    inner class FetchTemplates {

        @Test
        fun `returns empty list when no templates available`() {
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList()
            }

            val action = SelectScaffoldTemplateAction(clientFactory = { mockClient })
            val result = action.fetchTemplates()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns templates on successful fetch`() {
            val templates = setOf(
                createTemplate(name = "template-1", displayName = "Template 1"),
                createTemplate(name = "template-2", displayName = "Template 2")
            )
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList(templates)
            }

            val action = SelectScaffoldTemplateAction(clientFactory = { mockClient })
            val result = action.fetchTemplates()

            assertEquals(2, result.size)
        }

        @Test
        fun `propagates exception on fetch error`() {
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doThrow RuntimeException("Network error")
            }

            val action = SelectScaffoldTemplateAction(clientFactory = { mockClient })

            val exception = assertThrows(RuntimeException::class.java) {
                action.fetchTemplates()
            }
            assertEquals("Network error", exception.message)
        }

        @Test
        fun `uses injected client factory`() {
            var clientCreated = false
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList()
            }

            val action = SelectScaffoldTemplateAction(clientFactory = {
                clientCreated = true
                mockClient
            })
            action.fetchTemplates()

            assertTrue(clientCreated, "Client factory should be invoked")
        }
    }
}
