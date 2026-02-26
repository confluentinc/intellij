package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.service.ScaffoldProjectService
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateSelectionDialog
import io.confluent.intellijplugin.scaffold.ui.TemplateOptionsFormDialog
import io.confluent.intellijplugin.scaffold.util.TemplateIdeFilter
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

class SelectScaffoldTemplateAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        @Suppress("InappropriateCoroutineScope")
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val client = ScaffoldHttpClient()
                val templateList = client.fetchTemplates()
                val templates = TemplateIdeFilter.sortByIdeAffinity(templateList.data.toList())

                withContext(Dispatchers.EDT) {
                    if (templates.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            KafkaMessagesBundle.message("scaffold.action.no.templates.message"),
                            KafkaMessagesBundle.message("scaffold.action.no.templates.title")
                        )
                        return@withContext
                    }

                    val selectionDialog = ScaffoldTemplateSelectionDialog(project, templates)
                    if (!selectionDialog.showAndGet()) return@withContext

                    val selected = selectionDialog.selectedTemplate ?: return@withContext
                    val spec = selected.spec
                    val templateName = spec.name ?: return@withContext
                    val displayName = spec.displayName ?: templateName

                    thisLogger().debug("Selected template: $displayName")

                    val optionsDialog = TemplateOptionsFormDialog(project, displayName, spec.options ?: emptyMap())
                    if (!optionsDialog.showAndGet()) return@withContext

                    val optionValues = optionsDialog.getOptionValues()
                    val outputDir = optionsDialog.getOutputDirectory()

                    @Suppress("InappropriateCoroutineScope")
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val service = ScaffoldProjectService.getInstance(project)
                            service.generateProject(
                                templateName = templateName,
                                options = optionValues,
                                outputDirectory = Path.of(outputDir)
                            )
                            service.openGeneratedProject(Path.of(outputDir))
                        } catch (ex: Exception) {
                            thisLogger().warn("Failed to generate project", ex)
                            withContext(Dispatchers.EDT) {
                                Messages.showErrorDialog(
                                    project,
                                    KafkaMessagesBundle.message("scaffold.action.generate.error.message", ex.message ?: "Unknown error"),
                                    KafkaMessagesBundle.message("scaffold.action.generate.error.title")
                                )
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                thisLogger().warn("Failed to fetch templates", ex)
                withContext(Dispatchers.EDT) {
                    Messages.showErrorDialog(
                        project,
                        KafkaMessagesBundle.message("scaffold.action.error.message", ex.message ?: "Unknown error"),
                        KafkaMessagesBundle.message("scaffold.action.error.title")
                    )
                }
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
