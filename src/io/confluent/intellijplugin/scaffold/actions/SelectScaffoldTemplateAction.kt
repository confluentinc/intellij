package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateSelectionDialog
import io.confluent.intellijplugin.scaffold.util.TemplateIdeFilter
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

                    val dialog = ScaffoldTemplateSelectionDialog(project, templates)
                    if (dialog.showAndGet()) {
                        val selected = dialog.selectedTemplate
                        if (selected != null) {
                            thisLogger().debug("Selected template: ${selected.spec.displayName ?: selected.spec.name}")
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
