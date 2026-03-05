package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateOptionsDialog
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateSelectionDialog
import io.confluent.intellijplugin.scaffold.util.IdeLanguageMapper
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

class SelectScaffoldTemplateAction(
    private val clientFactory: () -> ScaffoldHttpClient = { ScaffoldHttpClient() },
    private val dialogFactory: (Project, List<ScaffoldV1TemplateListDataInner>) -> ScaffoldTemplateSelectionDialog =
        ::ScaffoldTemplateSelectionDialog,
    private val optionsDialogFactory: (Project, ScaffoldV1TemplateListDataInner) -> ScaffoldTemplateOptionsDialog =
        ::ScaffoldTemplateOptionsDialog,
    private val templateSorter: (List<ScaffoldV1TemplateListDataInner>) -> List<ScaffoldV1TemplateListDataInner> =
        { templates -> IdeLanguageMapper.sortByPreferredLanguage(templates) },
    private val fileChooser: (Project) -> VirtualFile? = { project ->
        FileChooser.chooseFile(
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(KafkaMessagesBundle.message("scaffold.action.choose.location.title")),
            project,
            null
        )
    },
    private val projectOpener: (Path) -> Unit = { path ->
        ProjectManager.getInstance().loadAndOpenProject(path.toString())
    }
) : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        currentThreadCoroutineScope().launch {
            fetchAndShowTemplates(project)
        }
    }

    internal suspend fun fetchTemplates(): List<ScaffoldV1TemplateListDataInner> {
        val client = clientFactory()
        return client.fetchTemplates().data.toList()
    }

    internal suspend fun fetchAndShowTemplates(project: Project) {
        try {
            val templates = withBackgroundProgress(
                project,
                KafkaMessagesBundle.message("scaffold.action.fetch.templates.progress"),
                cancellable = true
            ) {
                fetchTemplates()
            }

            withContext(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement()) {
                if (project.isDisposed) return@withContext

                val sortedTemplates = templateSorter(templates)
                val dialog = dialogFactory(project, sortedTemplates)
                if (!dialog.showAndGet()) return@withContext

                val selected = dialog.selectedTemplate ?: return@withContext
                thisLogger().debug("Selected template: ${selected.spec.displayName ?: selected.spec.name}")

                val options = if (!selected.spec.options.isNullOrEmpty()) {
                    val optionsDialog = optionsDialogFactory(project, selected)
                    if (!optionsDialog.showAndGet()) return@withContext
                    optionsDialog.optionValues
                } else {
                    emptyMap()
                }

                val chosenDir = fileChooser(project) ?: return@withContext
                val targetDir = Path.of(chosenDir.path)

                val templateName = selected.spec.name ?: return@withContext

                val zipBytes = withBackgroundProgress(
                    project,
                    KafkaMessagesBundle.message("scaffold.action.apply.template.progress"),
                    cancellable = true
                ) {
                    val client = clientFactory()
                    client.applyTemplate(templateName, options = options)
                }

                extractZip(zipBytes, targetDir)

                try {
                    projectOpener(targetDir)
                } catch (ex: Exception) {
                    thisLogger().warn("Failed to open project", ex)
                    Messages.showErrorDialog(
                        project,
                        KafkaMessagesBundle.message("scaffold.action.open.project.error", ex.message ?: ""),
                        KafkaMessagesBundle.message("scaffold.action.error.title")
                    )
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            thisLogger().warn("Failed to fetch templates", ex)
            withContext(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement()) {
                if (project.isDisposed) return@withContext
                Messages.showErrorDialog(
                    project,
                    KafkaMessagesBundle.message(
                        "scaffold.action.error.message",
                        ex.message ?: KafkaMessagesBundle.message("error.report.unknown.error")
                    ),
                    KafkaMessagesBundle.message("scaffold.action.error.title")
                )
            }
        }
    }

    internal fun extractZip(zipBytes: ByteArray, targetDir: Path) {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryPath = targetDir.resolve(entry.name).normalize()
                require(entryPath.startsWith(targetDir)) {
                    "ZIP entry path traversal detected: ${entry.name}"
                }
                if (entry.isDirectory) {
                    Files.createDirectories(entryPath)
                } else {
                    Files.createDirectories(entryPath.parent)
                    Files.newOutputStream(entryPath).use { out ->
                        zis.copyTo(out)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
