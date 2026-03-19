package io.confluent.intellijplugin.actions.dev.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.table.JBTable
import io.confluent.intellijplugin.consumer.editor.performance.BenchmarkConfig
import io.confluent.intellijplugin.consumer.editor.performance.PerformanceMetrics
import io.confluent.intellijplugin.consumer.editor.performance.PerformanceReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

class JcefBenchmarkPanel(
    private val project: Project,
    private val config: BenchmarkConfig
) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var runner: PerformanceBenchmarkRunner
    private var allMetrics: List<PerformanceMetrics> = emptyList()

    val component: JPanel
    private val progressBar: JProgressBar
    private val statusLabel: JLabel
    private val resultsTable: JBTable
    private val resultsTableModel: DefaultTableModel
    private val exportButton: JButton

    init {
        progressBar = JProgressBar(0, 100).apply {
            isStringPainted = true
            string = "Ready to start"
        }

        statusLabel = JLabel("Initializing benchmark...")

        // Comparison table: Operation | Rows | JTable (ms) | JCEF (ms) | Faster | Ratio
        resultsTableModel = object : DefaultTableModel(
            arrayOf("Operation", "Rows", "JTable (ms)", "JTable rows/s", "JCEF (ms)", "JCEF rows/s", "Faster", "Ratio"),
            0
        ) {
            override fun isCellEditable(row: Int, column: Int) = false
        }

        resultsTable = JBTable(resultsTableModel).apply {
            setShowGrid(true)
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        }

        exportButton = JButton("Export to CSV").apply {
            isEnabled = false
            addActionListener { exportResults() }
        }

        val progressPanel = JPanel(BorderLayout(5, 5)).apply {
            add(statusLabel, BorderLayout.NORTH)
            add(progressBar, BorderLayout.CENTER)
        }

        val resultsScrollPane = JBScrollPane(resultsTable)

        val buttonPanel = JPanel().apply {
            add(exportButton)
        }

        component = JPanel(BorderLayout(10, 10)).apply {
            add(progressPanel, BorderLayout.NORTH)
            add(resultsScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
            preferredSize = Dimension(900, 600)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
    }

    fun runBenchmark() {
        try {
            val jtable = JTableBenchmark(this)
            val jcef = if (JBCefApp.isSupported()) {
                JcefTableBenchmark(this)
            } else {
                null
            }

            runner = PerformanceBenchmarkRunner(
                config, jtable,
                jcef ?: run {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "JCEF not supported - running JTable only"
                    }
                    jtable // fallback: compare JTable against itself (not useful, but won't crash)
                },
                scope
            )

            // Add preview panels so the components are visible (required for accurate rendering)
            val previewPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(JPanel(BorderLayout()).apply {
                    add(JLabel("JTable Preview:"), BorderLayout.NORTH)
                    add(jtable.component, BorderLayout.CENTER)
                    preferredSize = Dimension(350, 180)
                })
                if (jcef != null) {
                    add(Box.createHorizontalStrut(5))
                    add(JPanel(BorderLayout()).apply {
                        add(JLabel("JCEF Preview:"), BorderLayout.NORTH)
                        add(jcef.component, BorderLayout.CENTER)
                        preferredSize = Dimension(350, 180)
                    })
                }
                maximumSize = Dimension(Int.MAX_VALUE, 200)
            }

            // Insert preview between results and buttons
            val currentSouth = component.getComponent(2) // button panel
            component.remove(currentSouth)
            val bottomPanel = JPanel(BorderLayout(5, 5)).apply {
                add(previewPanel, BorderLayout.CENTER)
                add(currentSouth, BorderLayout.SOUTH)
            }
            component.add(bottomPanel, BorderLayout.SOUTH)
            component.revalidate()
            component.repaint()

            runner.onProgress = { status, current, total ->
                SwingUtilities.invokeLater {
                    statusLabel.text = status
                    progressBar.value = (current * 100) / total
                    progressBar.string = "$current / $total tests"
                }
            }

            runner.onComplete = { metrics ->
                SwingUtilities.invokeLater {
                    allMetrics = metrics
                    updateComparisonTable(metrics)
                    statusLabel.text = "Benchmark completed"
                    progressBar.value = 100
                    progressBar.string = "Complete"
                    exportButton.isEnabled = true
                }
            }

            runner.runBenchmarks()
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                statusLabel.text = "Error: ${e.message}"
                Messages.showErrorDialog(
                    project,
                    "Failed to start benchmark: ${e.message}",
                    "Benchmark Error"
                )
            }
        }
    }

    private fun updateComparisonTable(metrics: List<PerformanceMetrics>) {
        resultsTableModel.rowCount = 0

        // Group by (operation, rowCount), pair JTable vs JCEF
        val grouped = metrics.groupBy { it.operationName to it.rowCount }

        for ((key, group) in grouped.toSortedMap(compareBy<Pair<String, Int>> { it.first }.thenBy { it.second })) {
            val jtableResult = group.find { it.tableType == "JTable" }
            val jcefResult = group.find { it.tableType == "JCEF" }

            val jtableMs = jtableResult?.let { if (it.failed) "FAILED" else it.durationMs.toString() } ?: "-"
            val jtableRps = jtableResult?.let { if (it.failed) "-" else String.format("%.0f", it.rowsPerSecond) } ?: "-"
            val jcefMs = jcefResult?.let { if (it.failed) "FAILED" else it.durationMs.toString() } ?: "-"
            val jcefRps = jcefResult?.let { if (it.failed) "-" else String.format("%.0f", it.rowsPerSecond) } ?: "-"

            val faster: String
            val ratio: String
            if (jtableResult != null && jcefResult != null && !jtableResult.failed && !jcefResult.failed) {
                if (jtableResult.durationMs <= jcefResult.durationMs) {
                    faster = "JTable"
                    ratio = if (jtableResult.durationMs > 0) {
                        String.format("%.1fx", jcefResult.durationMs.toDouble() / jtableResult.durationMs)
                    } else "N/A"
                } else {
                    faster = "JCEF"
                    ratio = if (jcefResult.durationMs > 0) {
                        String.format("%.1fx", jtableResult.durationMs.toDouble() / jcefResult.durationMs)
                    } else "N/A"
                }
            } else {
                faster = "-"
                ratio = "-"
            }

            resultsTableModel.addRow(arrayOf<Any>(key.first, key.second, jtableMs, jtableRps, jcefMs, jcefRps, faster, ratio))
        }
    }

    private fun exportResults() {
        val fileChooser = JFileChooser().apply {
            dialogTitle = "Export Benchmark Results"
            selectedFile = File("table-benchmark-results.csv")
        }

        if (fileChooser.showSaveDialog(component) == JFileChooser.APPROVE_OPTION) {
            try {
                PerformanceReporter.exportToCsv(allMetrics, fileChooser.selectedFile)
                Messages.showInfoMessage(project, "Results exported to ${fileChooser.selectedFile.absolutePath}", "Export Successful")
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to export: ${e.message}", "Export Failed")
            }
        }
    }

    override fun dispose() {
        // Scope cancelled via SupervisorJob
    }
}
