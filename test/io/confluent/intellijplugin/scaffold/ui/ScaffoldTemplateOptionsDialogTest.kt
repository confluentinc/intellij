package io.confluent.intellijplugin.scaffold.ui

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBPasswordField
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateOption
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateSpec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.swing.JComboBox
import javax.swing.JTextField
import javax.swing.SwingUtilities

@TestApplication
class ScaffoldTemplateOptionsDialogTest {

    private val project = ProjectManager.getInstance().defaultProject
    private lateinit var testDisposable: com.intellij.openapi.Disposable

    @BeforeEach
    fun setUp() {
        testDisposable = Disposer.newDisposable("ScaffoldTemplateOptionsDialogTest")
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeAndWait {
            Disposer.dispose(testDisposable)
        }
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

    private fun createOption(
        displayName: String = "Option",
        description: String = "A description",
        hint: String? = null,
        format: String? = null,
        pattern: String? = null,
        patternDescription: String? = null,
        enum: List<String>? = null,
        initialValue: String? = null,
        minLength: Int? = 0,
        order: Int? = 0
    ) = Scaffoldv1TemplateOption(
        displayName = displayName,
        description = description,
        hint = hint,
        format = format,
        pattern = pattern,
        patternDescription = patternDescription,
        enum = enum,
        initialValue = initialValue,
        minLength = minLength,
        order = order
    )

    private fun createTemplate(
        options: Map<String, Scaffoldv1TemplateOption>? = null
    ): ScaffoldV1TemplateListDataInner {
        return ScaffoldV1TemplateListDataInner(
            metadata = ScaffoldV1TemplateMetadata(self = null),
            spec = Scaffoldv1TemplateSpec(
                name = "test-template",
                displayName = "Test Template",
                description = "A test template",
                options = options
            )
        )
    }

    private fun createDialog(
        template: ScaffoldV1TemplateListDataInner
    ): ScaffoldTemplateOptionsDialog {
        return onEdt {
            val dialog = ScaffoldTemplateOptionsDialog(project, template)
            Disposer.register(testDisposable, dialog.disposable)
            dialog
        }
    }

    @Nested
    inner class `sort options` {

        @Test
        fun `sorts by order ascending`() {
            val options = mapOf(
                "second" to createOption(displayName = "Second", order = 2),
                "first" to createOption(displayName = "First", order = 1)
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                val keys = dialog.fieldComponents.keys.toList()
                assertEquals(listOf("first", "second"), keys)
            }
        }

        @Test
        fun `treats null order as 0`() {
            val options = mapOf(
                "has-order" to createOption(displayName = "Has Order", order = 1),
                "null-order" to createOption(displayName = "Null Order", order = null)
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                val keys = dialog.fieldComponents.keys.toList()
                assertEquals("null-order", keys[0])
                assertEquals("has-order", keys[1])
            }
        }

        @Test
        fun `sorts alphabetically by key as tiebreaker`() {
            val options = mapOf(
                "bravo" to createOption(displayName = "Bravo", order = 1),
                "alpha" to createOption(displayName = "Alpha", order = 1)
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                val keys = dialog.fieldComponents.keys.toList()
                assertEquals(listOf("alpha", "bravo"), keys)
            }
        }
    }

    @Nested
    inner class `field generation` {

        @Test
        fun `creates text field for default option`() {
            val options = mapOf("name" to createOption(displayName = "Name"))
            val dialog = createDialog(createTemplate(options))

            onEdt {
                assertTrue(dialog.fieldComponents["name"] is JTextField)
            }
        }

        @Test
        fun `creates password field for password format`() {
            val options = mapOf("secret" to createOption(displayName = "Secret", format = "password"))
            val dialog = createDialog(createTemplate(options))

            onEdt {
                assertTrue(dialog.fieldComponents["secret"] is JBPasswordField)
            }
        }

        @Test
        fun `creates combo box for enum option`() {
            val options = mapOf("lang" to createOption(displayName = "Language", enum = listOf("Java", "Python")))
            val dialog = createDialog(createTemplate(options))

            onEdt {
                val combo = dialog.fieldComponents["lang"] as JComboBox<*>
                assertEquals(2, combo.itemCount)
                assertEquals("Java", combo.getItemAt(0))
                assertEquals("Python", combo.getItemAt(1))
            }
        }

        @Test
        fun `sets initial value on text field`() {
            val options = mapOf("name" to createOption(displayName = "Name", initialValue = "my-project"))
            val dialog = createDialog(createTemplate(options))

            onEdt {
                val field = dialog.fieldComponents["name"] as JTextField
                assertEquals("my-project", field.text)
            }
        }

        @Test
        fun `sets initial value on combo box`() {
            val options = mapOf(
                "lang" to createOption(
                    displayName = "Language",
                    enum = listOf("Java", "Python", "Go"),
                    initialValue = "Python"
                )
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                val combo = dialog.fieldComponents["lang"] as JComboBox<*>
                assertEquals("Python", combo.selectedItem)
            }
        }

        @Test
        fun `creates no fields for template with no options`() {
            val dialog = createDialog(createTemplate(options = null))

            onEdt {
                assertTrue(dialog.fieldComponents.isEmpty())
            }
        }

        @Test
        fun `sets initial value on password field`() {
            val options = mapOf(
                "secret" to createOption(displayName = "Secret", format = "password", initialValue = "s3cret")
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                val field = dialog.fieldComponents["secret"] as JBPasswordField
                assertEquals("s3cret", String(field.password))
            }
        }
    }

    @Nested
    inner class `doOKAction` {

        @Test
        fun `collects values from text fields`() {
            val options = mapOf("name" to createOption(displayName = "Name", initialValue = "my-project"))
            val dialog = createDialog(createTemplate(options))

            onEdt {
                dialog.doOKAction()
            }

            assertEquals("my-project", dialog.optionValues["name"])
        }

        @Test
        fun `collects values from combo boxes`() {
            val options = mapOf(
                "lang" to createOption(
                    displayName = "Language",
                    enum = listOf("Java", "Python"),
                    initialValue = "Python"
                )
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                dialog.doOKAction()
            }

            assertEquals("Python", dialog.optionValues["lang"])
        }

        @Test
        fun `collects values from password fields`() {
            val options = mapOf(
                "secret" to createOption(displayName = "Secret", format = "password", initialValue = "p@ss")
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                dialog.doOKAction()
            }

            assertEquals("p@ss", dialog.optionValues["secret"])
        }

        @Test
        fun `collects values from multiple field types`() {
            val options = mapOf(
                "name" to createOption(displayName = "Name", initialValue = "test", order = 1),
                "lang" to createOption(
                    displayName = "Language",
                    enum = listOf("Java", "Python"),
                    initialValue = "Java",
                    order = 2
                ),
                "key" to createOption(displayName = "API Key", format = "password", initialValue = "abc", order = 3)
            )
            val dialog = createDialog(createTemplate(options))

            onEdt {
                dialog.doOKAction()
            }

            assertEquals("test", dialog.optionValues["name"])
            assertEquals("Java", dialog.optionValues["lang"])
            assertEquals("abc", dialog.optionValues["key"])
        }
    }
}
