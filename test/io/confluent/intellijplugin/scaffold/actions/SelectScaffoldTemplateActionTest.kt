package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class SelectScaffoldTemplateActionTest {

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
}
