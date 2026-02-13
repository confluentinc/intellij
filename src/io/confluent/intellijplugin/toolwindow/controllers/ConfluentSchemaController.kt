package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Confluent Cloud schema list controller.
 *
 * Simple table view showing schemas. For MVP, this provides basic read-only access.
 * Future: Can be enhanced to match full Kafka registry controller functionality.
 */
internal class ConfluentSchemaController(
    private val project: Project,
    private val dataManager: CCloudClusterDataManager,
    private val mainController: ConfluentMainController
) : ComponentController {

    private val panel = JPanel(BorderLayout())

    init {
        try {
            val schemas = dataManager.getSchemas()

            if (schemas.isEmpty()) {
                panel.add(JLabel("No schemas available"), BorderLayout.CENTER)
            } else {
                val columnNames = arrayOf("Schema Subject", "Type", "Version")
                val data = schemas.map { schema ->
                    arrayOf<Any>(
                        schema.name,
                        schema.type?.presentable ?: "Unknown",
                        schema.version ?: "Loading..."
                    )
                }.toTypedArray()

                val tableModel = DefaultTableModel(data, columnNames)
                val table = JBTable(tableModel).apply {
                    setDefaultEditor(Any::class.java, null)
                    // Note: Schema detail navigation is handled through tree selection
                    // Double-click navigation would require schema registry ID context
                }

                val scrollPane = JBScrollPane(table)
                panel.add(scrollPane, BorderLayout.CENTER)
            }
        } catch (e: Exception) {
            panel.add(JLabel("Error loading schemas: ${e.message}"), BorderLayout.CENTER)
        }
    }

    override fun dispose() {}

    override fun getComponent(): JComponent = panel
}
