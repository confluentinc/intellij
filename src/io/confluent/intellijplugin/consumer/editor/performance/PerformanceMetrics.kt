package io.confluent.intellijplugin.consumer.editor.performance

import java.io.File

data class PerformanceMetrics(
    val operationName: String,
    val rowCount: Int,
    val durationMs: Long,
    val rowsPerSecond: Double,
    val memoryBeforeMB: Double,
    val memoryAfterMB: Double,
    val memoryDeltaMB: Double,
    val failed: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun failed(message: String, rowCount: Int) = PerformanceMetrics(
            operationName = "Failed",
            rowCount = rowCount,
            durationMs = -1,
            rowsPerSecond = 0.0,
            memoryBeforeMB = 0.0,
            memoryAfterMB = 0.0,
            memoryDeltaMB = 0.0,
            failed = true,
            errorMessage = message
        )
    }
}

data class BenchmarkConfig(
    val testSizes: List<Int>,
    val operations: List<String>,
    val iterations: Int
)

object PerformanceReporter {
    fun formatTable(metrics: List<PerformanceMetrics>): String {
        val sb = StringBuilder()
        sb.appendLine("=== JCEF Performance Benchmark Results ===\n")

        metrics.groupBy { it.operationName }.forEach { (operation, results) ->
            sb.appendLine("$operation:")
            sb.appendLine("┌─────────┬────────────┬──────────────┬─────────────┐")
            sb.appendLine("│ Rows    │ Duration   │ Rows/sec     │ Memory Δ    │")
            sb.appendLine("├─────────┼────────────┼──────────────┼─────────────┤")

            results.forEach { m ->
                if (!m.failed) {
                    sb.appendLine(
                        "│ ${m.rowCount.toString().padEnd(7)} │ " +
                        "${m.durationMs}ms".padEnd(10) + " │ " +
                        "${String.format("%.0f", m.rowsPerSecond)}".padEnd(12) + " │ " +
                        "${String.format("%+.1f MB", m.memoryDeltaMB)}".padEnd(11) + " │"
                    )
                } else {
                    sb.appendLine("│ ${m.rowCount.toString().padEnd(7)} │ FAILED: ${m.errorMessage}")
                }
            }

            sb.appendLine("└─────────┴────────────┴──────────────┴─────────────┘\n")
        }

        return sb.toString()
    }

    fun exportToCsv(metrics: List<PerformanceMetrics>, file: File) {
        file.bufferedWriter().use { writer ->
            writer.write("Operation,Rows,Duration (ms),Rows/sec,Memory Before (MB),Memory After (MB),Memory Delta (MB),Status\n")
            metrics.forEach { m ->
                writer.write("${m.operationName},${m.rowCount},${m.durationMs}," +
                    "${m.rowsPerSecond},${m.memoryBeforeMB},${m.memoryAfterMB}," +
                    "${m.memoryDeltaMB},${if (m.failed) "FAILED: ${m.errorMessage}" else "OK"}\n")
            }
        }
    }
}
