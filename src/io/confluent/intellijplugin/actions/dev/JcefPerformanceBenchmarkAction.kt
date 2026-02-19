package io.confluent.intellijplugin.actions.dev

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.jcef.JBCefApp
import io.confluent.intellijplugin.actions.dev.ui.JcefBenchmarkDialog
import io.confluent.intellijplugin.actions.dev.ui.JcefBenchmarkPanel
import java.awt.Dimension
import javax.swing.JComponent

class JcefPerformanceBenchmarkAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Check JCEF support
        if (!JBCefApp.isSupported()) {
            Messages.showErrorDialog(
                project,
                "JCEF is not supported on this platform",
                "JCEF Not Available"
            )
            return
        }

        val configDialog = JcefBenchmarkDialog(project)
        if (configDialog.showAndGet()) {
            val config = configDialog.getConfig()
            thisLogger().info("Starting benchmark with config: $config")

            if (config.testSizes.isEmpty() || config.operations.isEmpty()) {
                Messages.showWarningDialog(
                    project,
                    "Please select at least one test size and one operation.",
                    "No Tests Selected"
                )
                return
            }

            val panel = JcefBenchmarkPanel(project, config)

            // Show in NON-MODAL dialog to avoid EDT deadlock
            val resultsDialog = object : DialogWrapper(project, false) {
                init {
                    title = "JCEF Performance Benchmark Results"
                    init()
                }

                override fun createCenterPanel(): JComponent {
                    return panel.component
                }

                override fun getPreferredSize(): Dimension {
                    return Dimension(1000, 700)
                }
            }

            // Start benchmark asynchronously BEFORE showing dialog
            // (runBenchmark launches a coroutine and returns immediately)
            panel.runBenchmark()

            // Show NON-MODAL dialog (doesn't block EDT)
            resultsDialog.show()
        }
    }
}
