package io.confluent.intellijplugin.actions.dev

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.actions.dev.ui.JcefBenchmarkDialog
import io.confluent.intellijplugin.actions.dev.ui.JcefBenchmarkPanel
import java.awt.Dimension
import javax.swing.JComponent

class JcefPerformanceBenchmarkAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

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

            val resultsDialog = object : DialogWrapper(project, false) {
                init {
                    title = "Table Performance Benchmark: JTable vs JCEF"
                    init()
                }

                override fun createCenterPanel(): JComponent = panel.component

                override fun getPreferredSize() = Dimension(1000, 700)
            }

            panel.runBenchmark()
            resultsDialog.show()
        }
    }
}
