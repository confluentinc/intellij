package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateSelectionDialog
import io.confluent.intellijplugin.scaffold.util.IdeLanguageMapper
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectScaffoldTemplateAction(
    private val clientFactory: () -> ScaffoldHttpClient = { ScaffoldHttpClient() },
    private val dialogFactory: (Project, List<ScaffoldV1TemplateListDataInner>) -> ScaffoldTemplateSelectionDialog =
        ::ScaffoldTemplateSelectionDialog,
    private val templateSorter: (List<ScaffoldV1TemplateListDataInner>) -> List<ScaffoldV1TemplateListDataInner> =
        { templates -> IdeLanguageMapper.sortByPreferredLanguage(templates) }
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
            val sortedTemplates = withBackgroundProgress(
                project,
                KafkaMessagesBundle.message("scaffold.action.fetch.templates.progress"),
                cancellable = true
            ) {
                templateSorter(fetchTemplates())
            }

            withContext(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement()) {
                if (project.isDisposed) return@withContext

                val dialog = dialogFactory(project, sortedTemplates)
                if (dialog.showAndGet()) {
                    val selected = dialog.selectedTemplate
                    if (selected != null) {
                        thisLogger().debug("Selected template: ${selected.spec.displayName ?: selected.spec.name}")
                    }
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

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
