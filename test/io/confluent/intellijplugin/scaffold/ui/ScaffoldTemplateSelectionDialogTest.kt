package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateSpec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
class ScaffoldTemplateSelectionDialogTest {

    private val project = ProjectManager.getInstance().defaultProject
    private lateinit var testDisposable: com.intellij.openapi.Disposable

    @BeforeEach
    fun setUp() {
        testDisposable = Disposer.newDisposable("ScaffoldTemplateSelectionDialogTest")
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeAndWait {
            Disposer.dispose(testDisposable)
        }
    }

    private fun createTemplate(
        name: String = "test-template",
        displayName: String = "Test Template",
        description: String = "A test description",
        version: String = "1.0.0",
        language: String = "Java",
        tags: List<String> = listOf("kafka", "test")
    ): ScaffoldV1TemplateListDataInner {
        return ScaffoldV1TemplateListDataInner(
            metadata = ScaffoldV1TemplateMetadata(self = null),
            spec = Scaffoldv1TemplateSpec(
                name = name,
                displayName = displayName,
                description = description,
                version = version,
                language = language,
                tags = tags
            )
        )
    }

    private fun <T> onEdt(action: () -> T): T {
        var result: T? = null
        var error: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                result = action()
            } catch (e: Throwable) {
                error = e
            }
        }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun createDialog(
        templates: List<ScaffoldV1TemplateListDataInner>
    ): ScaffoldTemplateSelectionDialog {
        return onEdt {
            val dialog = ScaffoldTemplateSelectionDialog(project, templates)
            Disposer.register(testDisposable, dialog.disposable)
            dialog
        }
    }

    @Nested
    inner class `initial state` {

        @Test
        fun `populates details from first template on construction`() {
            val template = createTemplate(
                description = "Kafka Streams starter",
                language = "Python",
                version = "2.1.0",
                tags = listOf("streaming", "kafka")
            )
            val dialog = createDialog(listOf(template))

            assertEquals("Kafka Streams starter", dialog.descriptionArea.text)
            assertEquals("Python", dialog.languageLabel.text)
            assertEquals("2.1.0", dialog.versionLabel.text)
            assertEquals("streaming, kafka", dialog.tagsLabel.text)
        }

        @Test
        fun `sets dialog title from bundle`() {
            val dialog = createDialog(listOf(createTemplate()))

            assertEquals("Select Project Template", dialog.title)
        }

        @Test
        fun `selectedTemplate is null before OK`() {
            val dialog = createDialog(listOf(createTemplate()))

            assertNull(dialog.selectedTemplate)
        }
    }

    @Nested
    inner class `combo box selection` {

        @Test
        fun `updates details when selection changes to second template`() {
            val template1 = createTemplate(
                description = "First description",
                language = "Java",
                version = "1.0.0",
                tags = listOf("java")
            )
            val template2 = createTemplate(
                description = "Second description",
                language = "Python",
                version = "3.0.0",
                tags = listOf("python", "streaming")
            )
            val dialog = createDialog(listOf(template1, template2))

            assertEquals("First description", dialog.descriptionArea.text)

            onEdt { dialog.comboModel.selectedItem = template2 }

            assertEquals("Second description", dialog.descriptionArea.text)
            assertEquals("Python", dialog.languageLabel.text)
            assertEquals("3.0.0", dialog.versionLabel.text)
            assertEquals("python, streaming", dialog.tagsLabel.text)
        }

        @Test
        fun `updates details when switching back to first template`() {
            val template1 = createTemplate(
                description = "Alpha",
                language = "Go",
                version = "0.1.0",
                tags = listOf("go")
            )
            val template2 = createTemplate(
                description = "Beta",
                language = "Rust",
                version = "0.2.0",
                tags = listOf("rust")
            )
            val dialog = createDialog(listOf(template1, template2))

            onEdt {
                dialog.comboModel.selectedItem = template2
                dialog.comboModel.selectedItem = template1
            }

            assertEquals("Alpha", dialog.descriptionArea.text)
            assertEquals("Go", dialog.languageLabel.text)
            assertEquals("0.1.0", dialog.versionLabel.text)
            assertEquals("go", dialog.tagsLabel.text)
        }
    }

    @Nested
    inner class `null-safe display` {

        @Test
        fun `displays empty strings for null spec fields`() {
            val template = ScaffoldV1TemplateListDataInner(
                metadata = ScaffoldV1TemplateMetadata(self = null),
                spec = Scaffoldv1TemplateSpec(
                    name = "minimal",
                    displayName = null,
                    description = null,
                    version = null,
                    language = null,
                    tags = null
                )
            )
            val dialog = createDialog(listOf(template))

            assertEquals("", dialog.descriptionArea.text)
            assertEquals("", dialog.languageLabel.text)
            assertEquals("", dialog.versionLabel.text)
            assertEquals("", dialog.tagsLabel.text)
        }

        @Test
        fun `displays empty tags when tags list is empty`() {
            val template = createTemplate(tags = emptyList())
            val dialog = createDialog(listOf(template))

            assertEquals("", dialog.tagsLabel.text)
        }
    }
}
