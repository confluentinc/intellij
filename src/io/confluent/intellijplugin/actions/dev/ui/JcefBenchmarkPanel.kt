package io.confluent.intellijplugin.actions.dev.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
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

        statusLabel = JLabel("Configure and run benchmark")

        resultsTableModel = object : DefaultTableModel(
            arrayOf("Operation", "Rows", "Duration (ms)", "Rows/sec", "Memory Δ (MB)"),
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
            addActionListener {
                exportResults()
            }
        }

        val closeButton = JButton("Close").apply {
            addActionListener {
                Disposer.dispose(this@JcefBenchmarkPanel)
            }
        }

        // Progress panel
        val progressPanel = JPanel(BorderLayout(5, 5)).apply {
            add(statusLabel, BorderLayout.NORTH)
            add(progressBar, BorderLayout.CENTER)
        }

        // Results panel
        val resultsScrollPane = JBScrollPane(resultsTable)

        // Button panel
        val buttonPanel = JPanel().apply {
            add(exportButton)
            add(closeButton)
        }

        // Main panel layout - NOTE: We'll add JCEF preview after runner is created
        component = JPanel(BorderLayout(10, 10)).apply {
            add(progressPanel, BorderLayout.NORTH)
            add(resultsScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
            preferredSize = Dimension(800, 600)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
    }

    fun runBenchmark() {
        try {
            statusLabel.text = "Initializing benchmark..."
            progressBar.value = 0
            progressBar.string = "Starting..."

            runner = PerformanceBenchmarkRunner(config, scope, this)

            // Add JCEF browser preview to UI (important for browser initialization)
            val previewPanel = JPanel(BorderLayout()).apply {
                add(JLabel("JCEF Preview:"), BorderLayout.NORTH)
                add(runner.getBrowserComponent(), BorderLayout.CENTER)
                preferredSize = Dimension(300, 200)
            }

            // Create split pane with results on left, preview on right
            val currentCenter = component.getComponent(1)
            component.remove(1)
            val splitPane = javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT,
                currentCenter,
                previewPanel
            ).apply {
                resizeWeight = 0.7
                dividerLocation = 550
            }
            component.add(splitPane, BorderLayout.CENTER)
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
                    updateResults(metrics)
                    statusLabel.text = "Benchmark completed - ${metrics.size} results"
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
                    "Failed to start benchmark: ${e.message}\n\n${e.stackTraceToString().take(500)}",
                    "Benchmark Error"
                )
            }
        }
    }

    private fun updateResults(metrics: List<PerformanceMetrics>) {
        resultsTableModel.rowCount = 0

        metrics.forEach { m ->
            resultsTableModel.addRow(
                arrayOf(
                    m.operationName,
                    m.rowCount,
                    if (m.failed) "FAILED" else m.durationMs.toString(),
                    if (m.failed) m.errorMessage else String.format("%.0f", m.rowsPerSecond),
                    if (m.failed) "-" else String.format("%+.1f", m.memoryDeltaMB)
                )
            )
        }
    }

    private fun exportResults() {
        val fileChooser = JFileChooser().apply {
            dialogTitle = "Export Benchmark Results"
            selectedFile = File("jcef-benchmark-results.csv")
        }

        if (fileChooser.showSaveDialog(component) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                val metrics = mutableListOf<PerformanceMetrics>()
                for (i in 0 until resultsTableModel.rowCount) {
                    val operation = resultsTableModel.getValueAt(i, 0) as String
                    val rows = resultsTableModel.getValueAt(i, 1) as Int
                    val durationStr = resultsTableModel.getValueAt(i, 2) as String
                    val rowsPerSecStr = resultsTableModel.getValueAt(i, 3) as String
                    val memDeltaStr = resultsTableModel.getValueAt(i, 4) as String

                    if (durationStr == "FAILED") {
                        metrics.add(PerformanceMetrics.failed(rowsPerSecStr, rows))
                    } else {
                        metrics.add(
                            PerformanceMetrics(
                                operationName = operation,
                                rowCount = rows,
                                durationMs = durationStr.toLong(),
                                rowsPerSecond = rowsPerSecStr.toDouble(),
                                memoryBeforeMB = 0.0,
                                memoryAfterMB = 0.0,
                                memoryDeltaMB = memDeltaStr.replace("+", "").replace(" MB", "").toDouble()
                            )
                        )
                    }
                }

                PerformanceReporter.exportToCsv(metrics, file)
                Messages.showInfoMessage(
                    project,
                    "Results exported successfully to ${file.absolutePath}",
                    "Export Successful"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to export results: ${e.message}",
                    "Export Failed"
                )
            }
        }
    }

    override fun dispose() {
        // Scope will be cancelled automatically
    }
}
