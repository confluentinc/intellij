package io.confluent.intellijplugin.actions.dev.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.consumer.editor.JcefRecordsTable
import io.confluent.intellijplugin.consumer.editor.performance.BenchmarkConfig
import io.confluent.intellijplugin.consumer.editor.performance.PerformanceMetrics
import io.confluent.intellijplugin.consumer.editor.performance.SyntheticRecordGenerator
import kotlinx.coroutines.*

class PerformanceBenchmarkRunner(
    private val config: BenchmarkConfig,
    private val scope: CoroutineScope,
    parentDisposable: Disposable
) {
    private val jcefTable: JcefRecordsTable
    private val results = mutableListOf<PerformanceMetrics>()

    var onProgress: ((String, Int, Int) -> Unit)? = null
    var onComplete: ((List<PerformanceMetrics>) -> Unit)? = null

    init {
        // Create JCEF table with standard columns
        thisLogger().info("Creating JCEF table for benchmarking...")
        jcefTable = JcefRecordsTable(
            columnNames = listOf("Topic", "Timestamp", "Key", "Value", "Partition", "Offset"),
            isProducer = false,
            parentDisposable = parentDisposable
        )
        Disposer.register(parentDisposable, jcefTable)
        thisLogger().info("JCEF table created successfully")
    }

    fun getBrowserComponent() = jcefTable.component

    fun runBenchmarks() {
        // Debug: println("=== BENCHMARK: runBenchmarks() called ===")
        scope.launch(Dispatchers.Default) {
            // Debug: println("=== BENCHMARK: Coroutine started on Dispatchers.Default ===")
            thisLogger().info("BENCHMARK: Starting benchmarks: sizes=${config.testSizes}, operations=${config.operations}, iterations=${config.iterations}")

            if (config.testSizes.isEmpty() || config.operations.isEmpty()) {
                // Debug: println("=== BENCHMARK: No test sizes or operations! ===")
                thisLogger().info("BENCHMARK: No test sizes or operations configured")
                onComplete?.invoke(emptyList())
                return@launch
            }

            // Wait for JCEF browser to initialize
            // Debug: println("=== BENCHMARK: Waiting for JCEF browser to initialize... ===")
            thisLogger().info("BENCHMARK: Waiting for JCEF browser to initialize...")
            onProgress?.invoke("Waiting for browser initialization...", 0, 100)
            delay(2000) // Give browser time to load and be ready
            // Debug: println("=== BENCHMARK: Browser should be ready, starting tests ===")
            thisLogger().info("BENCHMARK: Browser should be ready, starting tests")

            val totalTests = config.testSizes.size * config.operations.size * config.iterations
            var currentTest = 0
            // Debug: println("=== BENCHMARK: Total tests to run: $totalTests ===")

            try {
                for (size in config.testSizes) {
                    // Debug: println("=== BENCHMARK: Processing size: $size ===")
                    for (operation in config.operations) {
                        // Debug: println("=== BENCHMARK: Processing operation: $operation ===")
                        for (iteration in 1..config.iterations) {
                            currentTest++
                            val progressMsg = "Testing $operation with $size rows (iteration $iteration/${config.iterations})"
                            // Debug: println("=== BENCHMARK: $progressMsg ===")
                            thisLogger().info("BENCHMARK: $progressMsg")
                            onProgress?.invoke(progressMsg, currentTest, totalTests)

                            // Debug: println("=== BENCHMARK: About to call benchmark for $operation ===")
                            val metrics = when (operation) {
                                "insert" -> {
                                    // Debug: println("=== BENCHMARK: Calling benchmarkBatchInsert($size) ===")
                                    benchmarkBatchInsert(size)
                                }
                                "replace" -> {
                                    // Debug: println("=== BENCHMARK: Calling benchmarkClearReplace($size) ===")
                                    benchmarkClearReplace(size)
                                }
                                "memory" -> {
                                    // Debug: println("=== BENCHMARK: Calling benchmarkMemoryStress($size) ===")
                                    benchmarkMemoryStress(size)
                                }
                                else -> {
                                    // Debug: println("=== BENCHMARK: Unknown operation: $operation ===")
                                    thisLogger().info("BENCHMARK: Unknown operation: $operation")
                                    null
                                }
                            }
                            // Debug: println("=== BENCHMARK: Benchmark call returned for $operation ===")

                            metrics?.let {
                                // Debug: println("=== BENCHMARK: Got metrics: $it ===")
                                thisLogger().info("BENCHMARK: Benchmark result: $it")
                                results.add(it)
                            }

                            // Small delay between tests
                            delay(500)
                        }
                    }
                }

                // Calculate averages if iterations > 1
                val aggregated = if (config.iterations > 1) {
                    aggregateResults(results, config.iterations)
                } else {
                    results
                }

                thisLogger().info("Benchmarks completed: ${aggregated.size} results")
                onComplete?.invoke(aggregated)
            } catch (e: Exception) {
                thisLogger().error("Benchmark execution failed", e)
                onComplete?.invoke(results)
            }
        }
    }

    private suspend fun benchmarkBatchInsert(rowCount: Int): PerformanceMetrics {
        return try {
            // Debug: println("=== BENCHMARK: Starting insert for $rowCount rows ===")
            thisLogger().info("BENCHMARK: Generating $rowCount synthetic records...")

            // Generate synthetic records
            val records = SyntheticRecordGenerator.generateRecords(rowCount)
            // Debug: println("=== BENCHMARK: Generated $rowCount records ===")
            thisLogger().info("BENCHMARK: Generated $rowCount records")

            // Measure memory before
            // Debug: println("=== BENCHMARK: Running GC... ===")
            System.gc()
            delay(50) // Reduced delay
            val memBefore = getMemoryUsageMB()
            // Debug: println("=== BENCHMARK: Memory before: $memBefore MB ===")
            thisLogger().info("BENCHMARK: Memory before: $memBefore MB")

            // Wait for browser ready (with timeout)
            // Debug: println("=== BENCHMARK: Waiting for browser ready... ===")
            val browserReady = waitForBrowserReady(timeoutMs = 5000)
            if (!browserReady) {
                // Debug: println("=== BENCHMARK: Browser timeout! ===")
                thisLogger().info("BENCHMARK: Browser not ready timeout")
                return PerformanceMetrics.failed("Browser timeout", rowCount)
            }
            // Debug: println("=== BENCHMARK: Browser ready ===")

            // Execute and measure
            // Debug: println("=== BENCHMARK: Adding $rowCount rows to JCEF table... ===")
            thisLogger().info("BENCHMARK: About to add rows to JCEF table")
            val startNano = System.nanoTime()

            try {
                // Use invokeLater instead of withContext to avoid blocking
                // Debug: println("=== BENCHMARK: Using invokeLater to add rows... ===")
                val latch = java.util.concurrent.CountDownLatch(1)
                var exception: Exception? = null

                javax.swing.SwingUtilities.invokeLater {
                    try {
                        // Debug: println("=== BENCHMARK: EDT - calling addRows ===")
                        jcefTable.addRows(records)
                        // Debug: println("=== BENCHMARK: EDT - addRows returned ===")
                    } catch (e: Exception) {
                        // Debug: println("=== BENCHMARK: EDT - Exception: ${e.message} ===")
                        exception = e
                    } finally {
                        latch.countDown()
                    }
                }

                // Debug: println("=== BENCHMARK: Waiting for EDT to complete... ===")
                latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                // Debug: println("=== BENCHMARK: EDT completed or timed out ===")

                exception?.let { throw it }
            } catch (e: Exception) {
                // Debug: println("=== BENCHMARK: Exception in addRows: ${e.message} ===")
                e.printStackTrace()
                throw e
            }

            // Debug: println("=== BENCHMARK: Rows added, waiting for render completion... ===")
            thisLogger().info("BENCHMARK: Rows added, waiting for render")

            // Wait for rendering to complete (simple delay, since JCEF is async)
            delay(50 + (rowCount / 1000) * 25L) // Reduced delays

            val durationMs = (System.nanoTime() - startNano) / 1_000_000
            // Debug: println("=== BENCHMARK: Insert completed: ${durationMs}ms for $rowCount rows ===")
            thisLogger().info("BENCHMARK: Insert completed: ${durationMs}ms for $rowCount rows")

            // Measure memory after
            System.gc()
            delay(100)
            val memAfter = getMemoryUsageMB()
            thisLogger().info("Memory after: $memAfter MB (delta: ${memAfter - memBefore} MB)")

            // Clear for next test
            javax.swing.SwingUtilities.invokeLater {
                jcefTable.clear()
            }
            delay(200)

            PerformanceMetrics(
                operationName = "Batch Insert",
                rowCount = rowCount,
                durationMs = durationMs,
                rowsPerSecond = (rowCount / (durationMs / 1000.0)),
                memoryBeforeMB = memBefore,
                memoryAfterMB = memAfter,
                memoryDeltaMB = memAfter - memBefore
            )
        } catch (e: Exception) {
            thisLogger().error("Benchmark failed for $rowCount rows", e)
            PerformanceMetrics.failed("Exception: ${e.message}", rowCount)
        }
    }

    private suspend fun benchmarkClearReplace(rowCount: Int): PerformanceMetrics {
        return try {
            thisLogger().info("Starting clear & replace benchmark for $rowCount rows")
            // First insert some data
            val initialRecords = SyntheticRecordGenerator.generateRecords(rowCount)
            javax.swing.SwingUtilities.invokeLater {
                jcefTable.addRows(initialRecords)
            }
            delay(200)

            // Measure memory before
            System.gc()
            delay(100)
            val memBefore = getMemoryUsageMB()

            // Generate replacement data
            val newRecords = SyntheticRecordGenerator.generateRecords(rowCount)

            // Execute and measure
            val startNano = System.nanoTime()
            javax.swing.SwingUtilities.invokeLater {
                jcefTable.replaceAllRows(newRecords)
            }

            delay(100 + (rowCount / 1000) * 50L)

            val durationMs = (System.nanoTime() - startNano) / 1_000_000

            // Measure memory after
            System.gc()
            delay(100)
            val memAfter = getMemoryUsageMB()

            // Clear for next test
            // Debug: println("=== BENCHMARK: Clearing table... ===")
            javax.swing.SwingUtilities.invokeLater {
                jcefTable.clear()
            }
            delay(200)

            thisLogger().info("Clear & replace completed: ${durationMs}ms")
            PerformanceMetrics(
                operationName = "Clear & Replace",
                rowCount = rowCount,
                durationMs = durationMs,
                rowsPerSecond = (rowCount / (durationMs / 1000.0)),
                memoryBeforeMB = memBefore,
                memoryAfterMB = memAfter,
                memoryDeltaMB = memAfter - memBefore
            )
        } catch (e: Exception) {
            thisLogger().error("Clear & replace benchmark failed", e)
            PerformanceMetrics.failed("Exception: ${e.message}", rowCount)
        }
    }

    private suspend fun benchmarkMemoryStress(rowCount: Int): PerformanceMetrics {
        return try {
            thisLogger().info("Starting memory stress test for $rowCount rows (5 cycles)")
            System.gc()
            delay(100)
            val memBefore = getMemoryUsageMB()

            val startNano = System.nanoTime()

            // Perform multiple insert/clear cycles
            repeat(5) { cycle ->
                thisLogger().info("Memory stress cycle ${cycle + 1}/5")
                val records = SyntheticRecordGenerator.generateRecords(rowCount)
                javax.swing.SwingUtilities.invokeLater {
                    jcefTable.addRows(records)
                }
                delay(100)
                javax.swing.SwingUtilities.invokeLater {
                    jcefTable.clear()
                }
                delay(100)
            }

            val durationMs = (System.nanoTime() - startNano) / 1_000_000

            System.gc()
            delay(100)
            val memAfter = getMemoryUsageMB()

            thisLogger().info("Memory stress completed: ${durationMs}ms, memory delta: ${memAfter - memBefore} MB")
            PerformanceMetrics(
                operationName = "Memory Stress",
                rowCount = rowCount,
                durationMs = durationMs,
                rowsPerSecond = (rowCount * 5 / (durationMs / 1000.0)),
                memoryBeforeMB = memBefore,
                memoryAfterMB = memAfter,
                memoryDeltaMB = memAfter - memBefore
            )
        } catch (e: Exception) {
            thisLogger().error("Memory stress test failed", e)
            PerformanceMetrics.failed("Exception: ${e.message}", rowCount)
        }
    }

    private suspend fun waitForBrowserReady(timeoutMs: Long): Boolean {
        // Debug: println("=== BENCHMARK: waitForBrowserReady - sleeping for ${timeoutMs}ms ===")
        delay(timeoutMs)
        // Debug: println("=== BENCHMARK: waitForBrowserReady - done, returning true ===")
        return true
    }

    private fun getMemoryUsageMB(): Double {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory())
        return usedMemory / (1024.0 * 1024.0)
    }

    private fun aggregateResults(
        results: List<PerformanceMetrics>,
        iterations: Int
    ): List<PerformanceMetrics> {
        return results.groupBy { it.operationName to it.rowCount }
            .map { (key, group) ->
                val avgDuration = group.map { it.durationMs }.average().toLong()
                val avgRowsPerSec = group.map { it.rowsPerSecond }.average()
                val avgMemDelta = group.map { it.memoryDeltaMB }.average()

                PerformanceMetrics(
                    operationName = key.first,
                    rowCount = key.second,
                    durationMs = avgDuration,
                    rowsPerSecond = avgRowsPerSec,
                    memoryBeforeMB = group.first().memoryBeforeMB,
                    memoryAfterMB = group.first().memoryAfterMB,
                    memoryDeltaMB = avgMemDelta
                )
            }
    }
}
