package io.confluent.intellijplugin.actions.dev.ui

import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.consumer.editor.performance.BenchmarkConfig
import io.confluent.intellijplugin.consumer.editor.performance.PerformanceMetrics
import io.confluent.intellijplugin.consumer.editor.performance.SyntheticRecordGenerator
import kotlinx.coroutines.*

class PerformanceBenchmarkRunner(
    private val config: BenchmarkConfig,
    private val jtable: TableBenchmark,
    private val jcef: TableBenchmark,
    private val scope: CoroutineScope
) {
    private val results = mutableListOf<PerformanceMetrics>()

    var onProgress: ((String, Int, Int) -> Unit)? = null
    var onComplete: ((List<PerformanceMetrics>) -> Unit)? = null

    fun runBenchmarks() {
        scope.launch(Dispatchers.Default) {
            onProgress?.invoke("Waiting for initialization...", 0, 100)
            jtable.waitUntilReady()
            jcef.waitUntilReady()
            delay(1000) // settling time for JCEF browser

            // Warmup pass: run a small insert+clear on both implementations
            // to pay JIT compilation and class loading costs before measuring
            val warmupRecords = SyntheticRecordGenerator.generateRecords(100)
            for (benchmark in listOf(jtable, jcef)) {
                onProgress?.invoke("Warming up ${benchmark.name}...", 0, 100)
                benchmark.benchmarkAddRows(warmupRecords)
                benchmark.cleanup()
                delay(200)
            }

            val benchmarks = listOf(jtable, jcef)
            val totalTests = config.testSizes.size * config.operations.size * config.iterations * benchmarks.size
            var currentTest = 0

            try {
                for (size in config.testSizes) {
                    thisLogger().info("Generating $size synthetic records...")
                    val records = SyntheticRecordGenerator.generateRecords(size)

                    for (operation in config.operations) {
                        for (iteration in 1..config.iterations) {
                            for (benchmark in benchmarks) {
                                currentTest++
                                val label = "${benchmark.name}: $operation $size rows (iter $iteration/${config.iterations})"
                                thisLogger().info(label)
                                onProgress?.invoke(label, currentTest, totalTests)

                                val metrics = runOperation(benchmark, operation, records, size)
                                metrics?.let { results.add(it) }

                                delay(300) // settle between tests
                            }
                        }
                    }
                }

                val aggregated = if (config.iterations > 1) aggregateResults(results) else results
                thisLogger().info("Benchmark completed: ${aggregated.size} results")
                onComplete?.invoke(aggregated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().error("Benchmark execution failed", e)
                onComplete?.invoke(results)
            }
        }
    }

    private suspend fun runOperation(
        benchmark: TableBenchmark,
        operation: String,
        records: List<io.confluent.intellijplugin.consumer.editor.KafkaRecord>,
        size: Int
    ): PerformanceMetrics? {
        return try {
            val operationName: String
            val result: BenchmarkResult

            when (operation) {
                "insert" -> {
                    operationName = "Batch Insert"
                    benchmark.cleanup()
                    delay(100)
                    result = benchmark.benchmarkAddRows(records)
                }
                "replace" -> {
                    operationName = "Clear & Replace"
                    // Pre-fill with data, then measure the replace
                    benchmark.cleanup()
                    benchmark.benchmarkAddRows(records) // pre-fill (not measured)
                    delay(200)
                    val replacementRecords = SyntheticRecordGenerator.generateRecords(size)
                    result = benchmark.benchmarkReplaceAll(replacementRecords)
                }
                else -> return null
            }

            PerformanceMetrics(
                tableType = benchmark.name,
                operationName = operationName,
                rowCount = result.rowCount,
                durationMs = result.durationMs.toLong(),
                rowsPerSecond = if (result.durationMs > 0) result.rowCount / (result.durationMs / 1000.0) else 0.0
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().error("Benchmark failed: ${benchmark.name} $operation $size rows", e)
            PerformanceMetrics.failed(benchmark.name, "Exception: ${e.message}", size)
        }
    }

    private fun aggregateResults(results: List<PerformanceMetrics>): List<PerformanceMetrics> {
        return results
            .filter { !it.failed }
            .groupBy { Triple(it.tableType, it.operationName, it.rowCount) }
            .map { (key, group) ->
                val avgDuration = group.map { it.durationMs }.average().toLong()
                val avgRowsPerSec = group.map { it.rowsPerSecond }.average()
                PerformanceMetrics(
                    tableType = key.first,
                    operationName = key.second,
                    rowCount = key.third,
                    durationMs = avgDuration,
                    rowsPerSecond = avgRowsPerSec
                )
            }
    }
}
