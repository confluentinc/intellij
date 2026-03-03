package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.model.ScaffoldV1TemplateListDataInner
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateSelectionDialog
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.runBlocking

class SelectScaffoldTemplateAction(
    private val clientFactory: () -> ScaffoldHttpClient = { ScaffoldHttpClient() }
) : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            KafkaMessagesBundle.message("scaffold.action.fetch.templates.progress"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                fetchAndShowTemplates(project)
            }
        })
    }

    internal fun fetchTemplates(): List<ScaffoldV1TemplateListDataInner> {
        val client = clientFactory()
        return runBlocking { client.fetchTemplates() }.data.toList()
    }

    internal fun fetchAndShowTemplates(project: Project) {
        try {
            val templates = fetchTemplates()

            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed) return@invokeLater

                if (templates.isEmpty()) {
                    Messages.showInfoMessage(
                        project,
                        KafkaMessagesBundle.message("scaffold.action.no.templates.message"),
                        KafkaMessagesBundle.message("scaffold.action.no.templates.title")
                    )
                    return@invokeLater
                }

                val dialog = ScaffoldTemplateSelectionDialog(project, templates)
                if (dialog.showAndGet()) {
                    val selected = dialog.selectedTemplate
                    if (selected != null) {
                        thisLogger().debug("Selected template: ${selected.spec.displayName ?: selected.spec.name}")
                    }
                }
            }, project.disposed)
        } catch (ex: Exception) {
            thisLogger().warn("Failed to fetch templates", ex)
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed) return@invokeLater
                Messages.showErrorDialog(
                    project,
                    KafkaMessagesBundle.message(
                        "scaffold.action.error.message",
                        ex.message ?: KafkaMessagesBundle.message("error.report.unknown.error")
                    ),
                    KafkaMessagesBundle.message("scaffold.action.error.title")
                )
            }, project.disposed)
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
