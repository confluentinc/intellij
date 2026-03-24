package io.confluent.intellijplugin.consumer.editor.performance

import java.io.File

data class PerformanceMetrics(
    val tableType: String = "",
    val operationName: String,
    val rowCount: Int,
    val durationMs: Long,
    val rowsPerSecond: Double,
    val failed: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun failed(tableType: String, message: String, rowCount: Int) = PerformanceMetrics(
            tableType = tableType,
            operationName = "Failed",
            rowCount = rowCount,
            durationMs = -1,
            rowsPerSecond = 0.0,
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
    fun exportToCsv(metrics: List<PerformanceMetrics>, file: File) {
        file.bufferedWriter().use { writer ->
            writer.write("Table Type,Operation,Rows,Duration (ms),Rows/sec,Status\n")
            metrics.forEach { m ->
                writer.write("${m.tableType},${m.operationName},${m.rowCount},${m.durationMs}," +
                    "${m.rowsPerSecond},${if (m.failed) "FAILED: ${m.errorMessage}" else "OK"}\n")
            }
        }
    }
}
