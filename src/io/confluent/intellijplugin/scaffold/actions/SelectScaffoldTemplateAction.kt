package io.confluent.intellijplugin.scaffold.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import io.confluent.intellijplugin.scaffold.models.TemplateDisplayInfo
import io.confluent.intellijplugin.scaffold.ui.ScaffoldTemplateSelectionDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Action to browse and select Confluent scaffold templates.
 * Accessible via Cmd+Shift+A (or Ctrl+Shift+A on Windows/Linux).
 */
class SelectScaffoldTemplateAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        // Load templates asynchronously, then show dialog
        CoroutineScope(Dispatchers.Default).launch {
            try {
                println("Action: Loading templates...")
                val httpClient = ScaffoldHttpClient()
                val templateList = httpClient.fetchTemplates("vscode")

                println("Action: Received ${templateList.data.size} templates, converting to display info...")

                // Convert to display info
                val templates = templateList.data.map { template ->
                    TemplateDisplayInfo.from(template)
                }

                println("Action: Converted templates, showing dialog on Main thread...")

                // Show dialog on Main dispatcher
                withContext(Dispatchers.Main) {
                    if (templates.isEmpty()) {
                        Messages.showInfoMessage(project, "No templates available.", "Scaffold Templates")
                        return@withContext
                    }

                    println("Action: Creating and showing dialog with ${templates.size} templates")
                    ScaffoldTemplateSelectionDialog(project, templates).show()
                }
            } catch (ex: Exception) {
                println("Action: ERROR loading templates: ${ex.message}")
                ex.printStackTrace()
                withContext(Dispatchers.Main) {
                    Messages.showErrorDialog(
                        project,
                        "Failed to load scaffold templates: ${ex.message}",
                        "Scaffold Templates Error"
                    )
                }
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
