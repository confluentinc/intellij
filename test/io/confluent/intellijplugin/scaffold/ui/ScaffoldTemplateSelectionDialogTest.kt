package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
class ScaffoldTemplateSelectionDialogTest {

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
    @DisplayName("description area")
    inner class DescriptionArea {

        @Test
        fun `description area is not focusable`() {
            val templates = listOf(createTemplate())
            val project = ProjectManager.getInstance().defaultProject

            SwingUtilities.invokeAndWait {
                val dialog = ScaffoldTemplateSelectionDialog(project, templates)
                try {
                    val descriptionField = ScaffoldTemplateSelectionDialog::class.java
                        .getDeclaredField("descriptionArea")
                    descriptionField.isAccessible = true
                    val descriptionArea = descriptionField.get(dialog) as javax.swing.JTextArea

                    assertFalse(descriptionArea.isFocusable, "Description area should not be focusable")
                    assertFalse(descriptionArea.isEditable, "Description area should not be editable")
                } finally {
                    Disposer.dispose(dialog.disposable)
                }
            }
        }
    }
}
