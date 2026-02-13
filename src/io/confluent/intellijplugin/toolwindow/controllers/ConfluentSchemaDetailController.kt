package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.monitoring.toolwindow.DetailsMonitoringController
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Confluent Cloud schema detail controller.
 *
 * Simple detail view showing schema information. For MVP, this provides basic read-only access.
 * Future: Can be enhanced with version comparison, structure view, etc.
 */
internal class ConfluentSchemaDetailController(
    private val project: Project,
    private val dataManager: CCloudClusterDataManager
) : ComponentController, DetailsMonitoringController<String> {

    private val panel = JPanel(BorderLayout())
    private var currentSchemaName: String? = null

    override fun dispose() {}

    override fun getComponent(): JComponent = panel

    override fun setDetailsId(@Nls id: String) {
        currentSchemaName = id
        loadSchemaDetails(id)
    }

    private fun loadSchemaDetails(schemaName: String) {
        panel.removeAll()

        try {
            val schema = dataManager.getCachedOrLoadSchema(schemaName)
            val versionInfo = dataManager.getLatestVersionInfo(schemaName)

            val detailsPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(10)
            }

            // Header with schema metadata
            val headerText = buildString {
                appendLine("Schema: $schemaName")
                appendLine("Type: ${schema.type?.presentable ?: "Unknown"}")
                appendLine("Latest Version: ${schema.version ?: "N/A"}")
                if (versionInfo != null) {
                    appendLine()
                    appendLine("Schema Definition:")
                }
            }

            val headerLabel = JLabel("<html>${headerText.replace("\n", "<br/>")}</html>").apply {
                border = JBUI.Borders.emptyBottom(10)
            }
            detailsPanel.add(headerLabel, BorderLayout.NORTH)

            // Schema content (if available)
            if (versionInfo != null) {
                val schemaTextArea = JTextArea(versionInfo.schema).apply {
                    isEditable = false
                    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                    lineWrap = false
                    wrapStyleWord = false
                }

                val scrollPane = JBScrollPane(schemaTextArea)
                detailsPanel.add(scrollPane, BorderLayout.CENTER)
            }

            panel.add(detailsPanel, BorderLayout.CENTER)

        } catch (e: Exception) {
            thisLogger().warn("Failed to load schema details for '$schemaName'", e)
            panel.add(JLabel("Error loading schema: ${e.message}"), BorderLayout.CENTER)
        }

        panel.revalidate()
        panel.repaint()
    }
}
