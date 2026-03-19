package io.confluent.intellijplugin.actions.dev.ui

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.consumer.editor.performance.BenchmarkConfig
import javax.swing.JComponent

class JcefBenchmarkDialog(project: Project) : DialogWrapper(project) {
    private var size100 = true
    private var size1000 = true
    private var size5000 = false
    private var size10000 = true
    private var size25000 = false
    private var size50000 = true
    private var size100000 = true

    private var opInsert = true
    private var opReplace = true

    private var iterations = 3

    init {
        title = "Configure Table Performance Benchmark"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        group("Test Sizes") {
            row {
                checkBox("100 rows").bindSelected(::size100)
                checkBox("1,000 rows").bindSelected(::size1000)
                checkBox("5,000 rows").bindSelected(::size5000)
            }
            row {
                checkBox("10,000 rows").bindSelected(::size10000)
                checkBox("25,000 rows").bindSelected(::size25000)
                checkBox("50,000 rows").bindSelected(::size50000)
            }
            row {
                checkBox("100,000 rows").bindSelected(::size100000)
            }
        }

        group("Operations") {
            row {
                checkBox("Batch Insert").bindSelected(::opInsert)
            }
            row {
                checkBox("Clear & Replace").bindSelected(::opReplace)
            }
        }

        group("Options") {
            row("Iterations:") {
                intTextField(range = 1..10).bindIntText(::iterations)
                comment("Average results over multiple runs")
            }
        }
    }

    fun getConfig(): BenchmarkConfig {
        val testSizes = mutableListOf<Int>()
        if (size100) testSizes.add(100)
        if (size1000) testSizes.add(1000)
        if (size5000) testSizes.add(5000)
        if (size10000) testSizes.add(10000)
        if (size25000) testSizes.add(25000)
        if (size50000) testSizes.add(50000)
        if (size100000) testSizes.add(100000)

        val operations = mutableListOf<String>()
        if (opInsert) operations.add("insert")
        if (opReplace) operations.add("replace")

        val config = BenchmarkConfig(
            testSizes = testSizes.sorted(),
            operations = operations,
            iterations = iterations
        )

        thisLogger().info("Config: $config")
        return config
    }
}
