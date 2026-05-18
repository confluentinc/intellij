package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListMetadata
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateMetadata
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateList
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateOption
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateSpec
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateOptionsDialog
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateSelectionDialog
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class SelectScaffoldTemplateActionTest {

    private fun createTemplateList(
        templates: Set<ScaffoldV1TemplateListDataInner> = setOf(createTemplate())
    ): Scaffoldv1TemplateList {
        return Scaffoldv1TemplateList(
            apiVersion = Scaffoldv1TemplateList.ApiVersion.scaffoldSlashV1,
            kind = Scaffoldv1TemplateList.Kind.TemplateList,
            metadata = ScaffoldV1TemplateListMetadata(),
            data = templates
        )
    }

    private fun createTemplate(
        name: String = "test-client-template",
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

    private fun createMockClientReturning(
        templates: Set<ScaffoldV1TemplateListDataInner>
    ): ScaffoldHttpClient = mock {
        onBlocking { fetchTemplates() } doReturn createTemplateList(templates)
    }

    private fun createMockDialogFactory(
        mockDialog: ScaffoldTemplateSelectionDialog
    ): (Project, List<ScaffoldV1TemplateListDataInner>) -> ScaffoldTemplateSelectionDialog = mock {
        on { invoke(any(), any()) } doReturn mockDialog
    }

    private fun createMockOptionsDialogFactory(
        mockDialog: ScaffoldTemplateOptionsDialog
    ): (Project, ScaffoldV1TemplateListDataInner, Map<String, String>) -> ScaffoldTemplateOptionsDialog = mock {
        on { invoke(any(), any(), any()) } doReturn mockDialog
    }

    private fun createTemplateWithOptions(
        name: String = "test-client-template",
        options: Map<String, Scaffoldv1TemplateOption>? = null
    ): ScaffoldV1TemplateListDataInner {
        return ScaffoldV1TemplateListDataInner(
            metadata = ScaffoldV1TemplateMetadata(self = null),
            spec = Scaffoldv1TemplateSpec(
                name = name,
                displayName = "Test Template",
                description = "A test template",
                version = "1.0.0",
                language = "Java",
                tags = listOf("kafka"),
                options = options
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
    @DisplayName("topicPrefills")
    inner class TopicPrefills {

        @Test
        fun `returns empty map when topic name is null`() {
            val action = SelectScaffoldTemplateAction()
            assertTrue(action.topicPrefills(null).isEmpty())
        }

        @Test
        fun `returns empty map when topic name is blank`() {
            val action = SelectScaffoldTemplateAction()
            assertTrue(action.topicPrefills("   ").isEmpty())
        }

        @Test
        fun `maps every known topic option key to the topic name`() {
            val action = SelectScaffoldTemplateAction()
            val prefills = action.topicPrefills("orders")
            assertEquals(
                SelectScaffoldTemplateAction.TOPIC_OPTION_KEYS.toSet(),
                prefills.keys
            )
            assertTrue(prefills.values.all { it == "orders" })
        }

        @Test
        fun `includes cc_topic to cover Confluent Cloud client templates`() {
            assertTrue(
                SelectScaffoldTemplateAction.TOPIC_OPTION_KEYS.contains("cc_topic"),
                "cc_topic must be a recognized topic prefill key since CCloud client templates use it"
            )
        }
    }

    @Nested
    @DisplayName("bootstrapPrefills")
    inner class BootstrapPrefills {

        @Test
        fun `returns empty map when bootstrap server is null`() {
            val action = SelectScaffoldTemplateAction()
            assertTrue(action.bootstrapPrefills(null).isEmpty())
        }

        @Test
        fun `returns empty map when bootstrap server is blank`() {
            val action = SelectScaffoldTemplateAction()
            assertTrue(action.bootstrapPrefills("  ").isEmpty())
        }

        @Test
        fun `maps every known bootstrap option key to the bootstrap server`() {
            val action = SelectScaffoldTemplateAction()
            val prefills = action.bootstrapPrefills("pkc-abc.us-west-2.aws.confluent.cloud:9092")
            assertEquals(
                SelectScaffoldTemplateAction.BOOTSTRAP_OPTION_KEYS.toSet(),
                prefills.keys
            )
            assertTrue(prefills.values.all { it == "pkc-abc.us-west-2.aws.confluent.cloud:9092" })
        }

        @Test
        fun `includes cc_bootstrap_server to cover Confluent Cloud client templates`() {
            assertTrue(
                SelectScaffoldTemplateAction.BOOTSTRAP_OPTION_KEYS.contains("cc_bootstrap_server"),
                "cc_bootstrap_server must be a recognized bootstrap prefill key since CCloud client templates use it"
            )
        }
    }

    @Nested
    @DisplayName("schemaRegistryPrefills")
    inner class SchemaRegistryPrefills {

        @Test
        fun `returns empty map when schema registry url is null`() {
            val action = SelectScaffoldTemplateAction()
            assertTrue(action.schemaRegistryPrefills(null).isEmpty())
        }

        @Test
        fun `returns empty map when schema registry url is blank`() {
            val action = SelectScaffoldTemplateAction()
            assertTrue(action.schemaRegistryPrefills("  ").isEmpty())
        }

        @Test
        fun `maps every known schema registry option key to the url`() {
            val action = SelectScaffoldTemplateAction()
            val url = "https://psrc-abc.us-west-2.aws.confluent.cloud"
            val prefills = action.schemaRegistryPrefills(url)
            assertEquals(
                SelectScaffoldTemplateAction.SCHEMA_REGISTRY_OPTION_KEYS.toSet(),
                prefills.keys
            )
            assertTrue(prefills.values.all { it == url })
        }

        @Test
        fun `includes cc_schema_registry_url to cover Confluent Cloud client templates`() {
            assertTrue(
                SelectScaffoldTemplateAction.SCHEMA_REGISTRY_OPTION_KEYS.contains("cc_schema_registry_url"),
                "cc_schema_registry_url must be a recognized SR prefill key since CCloud client templates use it"
            )
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {

        private val action = SelectScaffoldTemplateAction()

        private fun eventForPath(path: RfsPath?) = TestActionEvent.createTestEvent(action) { key ->
            when (key) {
                MainTreeController.RFS_PATH.name -> path
                else -> null
            }
        }

        @Test
        fun `hides action when rfsPath is null`() {
            val event = eventForPath(null)

            action.update(event)

            assertFalse(event.presentation.isEnabled)
            assertFalse(event.presentation.isVisible)
        }

        @Test
        fun `shows action when rfsPath parent is Topics folder`() {
            val event = eventForPath(RfsPath(listOf("Topics", "orders"), false))

            action.update(event)

            assertTrue(event.presentation.isEnabled)
            assertTrue(event.presentation.isVisible)
        }

        @Test
        fun `shows action when rfsPath parent is ccloud lkc cluster id`() {
            val event = eventForPath(RfsPath(listOf("lkc-abc123", "orders"), false))

            action.update(event)

            assertTrue(event.presentation.isEnabled)
            assertTrue(event.presentation.isVisible)
        }

        @Test
        fun `hides action when rfsPath is the Topics folder itself`() {
            val event = eventForPath(RfsPath(listOf("Topics"), true))

            action.update(event)

            assertFalse(event.presentation.isEnabled)
            assertFalse(event.presentation.isVisible)
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

        // actionPerformed delegates to fetchAndShowTemplates via coroutine launch.
        // The coroutine integration requires IntelliJ's action system context which
        // is not available in unit tests. The fetchAndShowTemplates method is tested
        // directly in the FetchAndShowTemplates nested class below.
    }

    @Nested
    @DisplayName("fetchTemplates")
    inner class FetchTemplates {

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
            val result = runBlocking { action.fetchTemplates() }

            assertEquals(2, result.size)
        }

        @Test
        fun `propagates exception on fetch error`() {
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doThrow RuntimeException("Network error")
            }

            val action = SelectScaffoldTemplateAction(clientFactory = { mockClient })

            val exception = assertThrows(RuntimeException::class.java) {
                runBlocking { action.fetchTemplates() }
            }
            assertEquals("Network error", exception.message)
        }
    }

    @Nested
    @DisplayName("getActionUpdateThread")
    inner class GetActionUpdateThread {

        @Test
        fun `returns BGT`() {
            val action = SelectScaffoldTemplateAction()
            assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
        }
    }

    @Nested
    @DisplayName("fetchAndShowTemplates")
    inner class FetchAndShowTemplates {

        private val project = ProjectManager.getInstance().defaultProject

        @Test
        fun `shows error dialog when fetch throws exception`() {
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doThrow RuntimeException("Connection refused")
            }
            val action = SelectScaffoldTemplateAction(clientFactory = { mockClient })

            var dialogMessage: String? = null
            TestDialogManager.setTestDialog { message ->
                dialogMessage = message
                0
            }
            try {
                runBlocking { action.fetchAndShowTemplates(project) }
            } finally {
                TestDialogManager.setTestDialog(TestDialog.DEFAULT)
            }

            assertNotNull(dialogMessage, "Error dialog should have been shown")
            assertTrue(
                dialogMessage!!.contains("Connection refused"),
                "Error dialog message should contain the exception message"
            )
        }

        @Test
        fun `shows error dialog with fallback message when exception has null message`() {
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doThrow RuntimeException(null as String?)
            }
            val action = SelectScaffoldTemplateAction(clientFactory = { mockClient })

            var dialogMessage: String? = null
            TestDialogManager.setTestDialog { message ->
                dialogMessage = message
                0
            }
            try {
                runBlocking { action.fetchAndShowTemplates(project) }
            } finally {
                TestDialogManager.setTestDialog(TestDialog.DEFAULT)
            }

            val expectedFallback = KafkaMessagesBundle.message("error.report.unknown.error")
            val expectedMessage = KafkaMessagesBundle.message("scaffold.action.error.message", expectedFallback)
            assertEquals(expectedMessage, dialogMessage, "Error dialog should show the bundle-resolved fallback message")
        }

        @Test
        fun `shows dialog with templates on successful fetch`() {
            val templates = setOf(
                createTemplate(name = "template-1-client", displayName = "Template 1"),
                createTemplate(name = "template-2-client", displayName = "Template 2")
            )
            val mockClient = createMockClientReturning(templates)

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn false
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory
            )

            val templatesCaptor = argumentCaptor<List<ScaffoldV1TemplateListDataInner>>()

            runBlocking { action.fetchAndShowTemplates(project) }

            verify(dialogFactory).invoke(eq(project), templatesCaptor.capture())
            assertEquals(
                setOf("template-1-client", "template-2-client"),
                templatesCaptor.firstValue.map { it.spec.name }.toSet()
            )
            verify(mockDialog).showAndGet()
        }

        @Test
        fun `applies template sorter before showing dialog`() {
            val templates = setOf(
                createTemplate(name = "template-a-client", displayName = "Template A"),
                createTemplate(name = "template-b-client", displayName = "Template B"),
                createTemplate(name = "template-c-client", displayName = "Template C")
            )
            val mockClient = createMockClientReturning(templates)

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn false
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val sorter: (List<ScaffoldV1TemplateListDataInner>) -> List<ScaffoldV1TemplateListDataInner> =
                { it.sortedByDescending { t -> t.spec.name } }

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                templateSorter = sorter
            )

            val templatesCaptor = argumentCaptor<List<ScaffoldV1TemplateListDataInner>>()

            runBlocking { action.fetchAndShowTemplates(project) }

            verify(dialogFactory).invoke(eq(project), templatesCaptor.capture())
            val sortedTemplates = templatesCaptor.firstValue
            assertEquals(
                listOf("template-c-client", "template-b-client", "template-a-client"),
                sortedTemplates.map { it.spec.name }
            )
        }

        @Test
        fun `filters out templates whose name does not contain client`() {
            val templates = setOf(
                createTemplate(name = "java-client", displayName = "Java Client"),
                createTemplate(name = "kafka-streams-example", displayName = "Streams Example"),
                createTemplate(name = "python-client", displayName = "Python Client")
            )
            val mockClient = createMockClientReturning(templates)

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn false
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory
            )

            val templatesCaptor = argumentCaptor<List<ScaffoldV1TemplateListDataInner>>()

            runBlocking { action.fetchAndShowTemplates(project) }

            verify(dialogFactory).invoke(eq(project), templatesCaptor.capture())
            assertEquals(
                setOf("java-client", "python-client"),
                templatesCaptor.firstValue.map { it.spec.name }.toSet()
            )
        }

        @Test
        fun `rethrows CancellationException for structured concurrency`() {
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doThrow CancellationException("cancelled")
            }
            val action = SelectScaffoldTemplateAction(clientFactory = { mockClient })

            assertThrows(CancellationException::class.java) {
                runBlocking { action.fetchAndShowTemplates(project) }
            }
        }

        @Test
        fun `handles null selected template when user confirms dialog`() {
            val templates = setOf(createTemplate())
            val mockClient = createMockClientReturning(templates)

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn null
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory
            )

            runBlocking { action.fetchAndShowTemplates(project) }

            verify(mockDialog).showAndGet()
            verify(mockDialog).selectedTemplate
        }

        @Test
        fun `does not show dialog when project is disposed`() {
            val templates = setOf(createTemplate())
            val mockClient = createMockClientReturning(templates)

            val mockDialog = mock<ScaffoldTemplateSelectionDialog>()
            val dialogFactory = createMockDialogFactory(mockDialog)

            val disposedProject = mock<Project> {
                on { isDisposed } doReturn true
                on { disposed } doReturn Condition<Project> { true }
            }

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory
            )

            runBlocking { action.fetchAndShowTemplates(disposedProject) }

            verify(dialogFactory, never()).invoke(any(), any())
            verify(mockDialog, never()).showAndGet()
        }

        @Test
        fun `shows options dialog after template selection when template has options`() {
            val templateWithOptions = createTemplateWithOptions(
                options = mapOf(
                    "name" to Scaffoldv1TemplateOption(
                        displayName = "Name",
                        description = "Project name"
                    )
                )
            )
            val mockClient = createMockClientReturning(setOf(templateWithOptions))

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn templateWithOptions
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val mockOptionsDialog = mock<ScaffoldTemplateOptionsDialog> {
                on { showAndGet() } doReturn false
            }
            val optionsDialogFactory = createMockOptionsDialogFactory(mockOptionsDialog)

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                optionsDialogFactory = optionsDialogFactory
            )

            runBlocking { action.fetchAndShowTemplates(project) }

            verify(mockOptionsDialog).showAndGet()
        }

        @Test
        fun `skips options dialog when template has no options`() {
            val templateNoOptions = createTemplateWithOptions(options = null)
            val mockClient = createMockClientReturning(setOf(templateNoOptions))

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn templateNoOptions
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val mockOptionsDialog = mock<ScaffoldTemplateOptionsDialog>()
            val optionsDialogFactory = createMockOptionsDialogFactory(mockOptionsDialog)

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                optionsDialogFactory = optionsDialogFactory,
                fileChooser = { null }
            )

            runBlocking { action.fetchAndShowTemplates(project) }

            verify(optionsDialogFactory, never()).invoke(any(), any(), any())
            verify(mockOptionsDialog, never()).showAndGet()
        }

        @Test
        fun `does not proceed when user cancels options dialog`() {
            val templateWithOptions = createTemplateWithOptions(
                options = mapOf(
                    "name" to Scaffoldv1TemplateOption(
                        displayName = "Name",
                        description = "Project name"
                    )
                )
            )
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList(setOf(templateWithOptions))
            }

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn templateWithOptions
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val mockOptionsDialog = mock<ScaffoldTemplateOptionsDialog> {
                on { showAndGet() } doReturn false
            }
            val optionsDialogFactory = createMockOptionsDialogFactory(mockOptionsDialog)

            var projectOpened = false
            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                optionsDialogFactory = optionsDialogFactory,
                projectOpener = { projectOpened = true }
            )

            runBlocking { action.fetchAndShowTemplates(project) }

            assertFalse(projectOpened, "Project should not be opened when options dialog is cancelled")
        }

        @Test
        fun `calls applyTemplate with collected options and opens project`() {
            val templateWithOptions = createTemplateWithOptions(
                name = "my-client-template",
                options = mapOf(
                    "name" to Scaffoldv1TemplateOption(
                        displayName = "Name",
                        description = "Project name"
                    )
                )
            )

            val zipBytes = createTestZipBytes()
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList(setOf(templateWithOptions))
                onBlocking { applyTemplate(any(), any(), any()) } doReturn zipBytes
            }

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn templateWithOptions
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val optionValues = mapOf("name" to "my-project")
            val mockOptionsDialog = mock<ScaffoldTemplateOptionsDialog> {
                on { showAndGet() } doReturn true
                on { this.optionValues } doReturn optionValues
            }
            val optionsDialogFactory = createMockOptionsDialogFactory(mockOptionsDialog)

            val tempDir = java.nio.file.Files.createTempDirectory("scaffold-test")
            val mockVirtualFile = mock<VirtualFile> {
                on { path } doReturn tempDir.toString()
            }

            var openedPath: Path? = null
            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                optionsDialogFactory = optionsDialogFactory,
                fileChooser = { mockVirtualFile },
                projectOpener = { path -> openedPath = path }
            )

            runBlocking { action.fetchAndShowTemplates(project) }

            runBlocking { verify(mockClient).applyTemplate(eq("my-client-template"), eq("intellij"), eq(optionValues)) }
            assertNotNull(openedPath, "Project should be opened")
            assertTrue(openedPath!!.startsWith(tempDir), "Project should be under chosen directory")
            val folderName = openedPath!!.fileName.toString()
            assertTrue(
                folderName.matches(Regex("^my-client-template-[0-9a-f]{8}$")),
                "Project folder should be '<template-name>-<8 hex chars>', was: $folderName"
            )
            // Verify the zip contents were extracted into the project dir
            assertTrue(Files.exists(openedPath!!.resolve("my-project/test.txt")), "Zip contents should be extracted into project dir")

            // Clean up
            tempDir.toFile().deleteRecursively()
        }

        @Test
        fun `shows error when apply fails`() {
            val templateNoOptions = createTemplateWithOptions(name = "fail-client-template", options = null)
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList(setOf(templateNoOptions))
                onBlocking { applyTemplate(any(), any(), any()) } doThrow RuntimeException("Apply failed")
            }

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn templateNoOptions
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val tempDir = java.nio.file.Files.createTempDirectory("scaffold-test")
            val mockVirtualFile = mock<VirtualFile> {
                on { path } doReturn tempDir.toString()
            }

            var dialogMessage: String? = null
            TestDialogManager.setTestDialog { message ->
                dialogMessage = message
                0
            }

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                fileChooser = { mockVirtualFile },
                projectOpener = { }
            )

            try {
                runBlocking { action.fetchAndShowTemplates(project) }
            } finally {
                TestDialogManager.setTestDialog(TestDialog.DEFAULT)
                tempDir.toFile().deleteRecursively()
            }

            assertNotNull(dialogMessage, "Error dialog should have been shown")
            assertTrue(dialogMessage!!.contains("Apply failed"))
        }

        @Test
        fun `does not proceed when user cancels file chooser`() {
            val templateNoOptions = createTemplateWithOptions(options = null)
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList(setOf(templateNoOptions))
            }

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn templateNoOptions
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            var projectOpened = false
            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                fileChooser = { null },
                projectOpener = { projectOpened = true }
            )

            runBlocking { action.fetchAndShowTemplates(project) }

            assertFalse(projectOpened, "Project should not be opened when file chooser is cancelled")
            runBlocking { verify(mockClient, never()).applyTemplate(any(), any(), any()) }
        }

        @Test
        fun `shows error dialog when projectOpener fails`() {
            val templateNoOptions = createTemplateWithOptions(name = "my-client-template", options = null)
            val zipBytes = createTestZipBytes()
            val mockClient = mock<ScaffoldHttpClient> {
                onBlocking { fetchTemplates() } doReturn createTemplateList(setOf(templateNoOptions))
                onBlocking { applyTemplate(any(), any(), any()) } doReturn zipBytes
            }

            val mockDialog = mock<ScaffoldTemplateSelectionDialog> {
                on { showAndGet() } doReturn true
                on { selectedTemplate } doReturn templateNoOptions
            }
            val dialogFactory = createMockDialogFactory(mockDialog)

            val tempDir = java.nio.file.Files.createTempDirectory("scaffold-test")
            val mockVirtualFile = mock<VirtualFile> {
                on { path } doReturn tempDir.toString()
            }

            var dialogMessage: String? = null
            TestDialogManager.setTestDialog { message ->
                dialogMessage = message
                0
            }

            val action = SelectScaffoldTemplateAction(
                clientFactory = { mockClient },
                dialogFactory = dialogFactory,
                fileChooser = { mockVirtualFile },
                projectOpener = { throw RuntimeException("Open failed") }
            )

            try {
                runBlocking { action.fetchAndShowTemplates(project) }
            } finally {
                TestDialogManager.setTestDialog(TestDialog.DEFAULT)
                tempDir.toFile().deleteRecursively()
            }

            assertNotNull(dialogMessage, "Error dialog should have been shown")
            assertTrue(dialogMessage!!.contains("Open failed"))
        }

        private fun createTestZipBytes(): ByteArray {
            val baos = java.io.ByteArrayOutputStream()
            val zos = java.util.zip.ZipOutputStream(baos)
            zos.putNextEntry(java.util.zip.ZipEntry("my-project/"))
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("my-project/test.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
            zos.close()
            return baos.toByteArray()
        }
    }

    @Nested
    @DisplayName("extractZip")
    inner class ExtractZip {

        @Test
        fun `extracts nested directories and files`() {
            val baos = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("src/"))
                zos.closeEntry()
                zos.putNextEntry(java.util.zip.ZipEntry("src/main.txt"))
                zos.write("contents".toByteArray())
                zos.closeEntry()
            }

            val targetDir = Files.createTempDirectory("extract-test")
            try {
                val action = SelectScaffoldTemplateAction()
                action.extractZip(baos.toByteArray(), targetDir)

                assertTrue(Files.exists(targetDir.resolve("src")))
                assertTrue(Files.isDirectory(targetDir.resolve("src")))
                assertTrue(Files.exists(targetDir.resolve("src/main.txt")))
                assertEquals("contents", Files.readString(targetDir.resolve("src/main.txt")))
            } finally {
                targetDir.toFile().deleteRecursively()
            }
        }

        @Test
        fun `creates parent directories implicitly for nested files`() {
            val baos = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("a/b/c/deep.txt"))
                zos.write("deep".toByteArray())
                zos.closeEntry()
            }

            val targetDir = Files.createTempDirectory("extract-test")
            try {
                val action = SelectScaffoldTemplateAction()
                action.extractZip(baos.toByteArray(), targetDir)

                assertTrue(Files.exists(targetDir.resolve("a/b/c/deep.txt")))
                assertEquals("deep", Files.readString(targetDir.resolve("a/b/c/deep.txt")))
            } finally {
                targetDir.toFile().deleteRecursively()
            }
        }

        @Test
        fun `rejects entries that escape target directory`() {
            val baos = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("../evil.txt"))
                zos.write("evil".toByteArray())
                zos.closeEntry()
            }

            val targetDir = Files.createTempDirectory("extract-test")
            try {
                val action = SelectScaffoldTemplateAction()
                val ex = assertThrows(IllegalArgumentException::class.java) {
                    action.extractZip(baos.toByteArray(), targetDir)
                }
                assertTrue(ex.message!!.contains("traversal"))
            } finally {
                targetDir.toFile().deleteRecursively()
            }
        }

    }
}
