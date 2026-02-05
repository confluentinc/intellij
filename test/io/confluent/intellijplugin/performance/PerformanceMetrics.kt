package io.confluent.intellijplugin.performance

import java.text.DecimalFormat
import kotlin.system.measureNanoTime

/**
 * Collects and reports performance metrics for load testing.
 */
class PerformanceMetrics(private val name: String) {
    private val measurements = mutableMapOf<String, MutableList<Long>>()
    private var startHeapBytes: Long = 0
    private var endHeapBytes: Long = 0

    private val decimalFormat = DecimalFormat("#,###")
    private val memoryFormat = DecimalFormat("#,###.##")

    /**
     * Captures current heap usage as the baseline.
     */
    fun captureHeapBaseline() {
        forceGc()
        startHeapBytes = usedHeapBytes()
    }

    /**
     * Captures current heap usage as the end state.
     */
    fun captureHeapEnd() {
        forceGc()
        endHeapBytes = usedHeapBytes()
    }

    /**
     * Returns heap growth in bytes since baseline.
     */
    fun heapGrowthBytes(): Long = endHeapBytes - startHeapBytes

    /**
     * Returns approximate bytes per record based on heap growth.
     */
    fun bytesPerRecord(recordCount: Int): Double {
        return if (recordCount > 0) heapGrowthBytes().toDouble() / recordCount else 0.0
    }

    /**
     * Times an operation and records the duration.
     */
    inline fun <T> measure(label: String, block: () -> T): T {
        val result: T
        val nanos = measureNanoTime { result = block() }
        record(label, nanos)
        return result
    }

    /**
     * Records a timing measurement in nanoseconds.
     */
    fun record(label: String, nanos: Long) {
        measurements.getOrPut(label) { mutableListOf() }.add(nanos)
    }

    /**
     * Gets the average time for a label in milliseconds.
     */
    fun averageMs(label: String): Double {
        val times = measurements[label] ?: return 0.0
        return times.average() / 1_000_000.0
    }

    /**
     * Gets the total time for a label in milliseconds.
     */
    fun totalMs(label: String): Double {
        val times = measurements[label] ?: return 0.0
        return times.sum() / 1_000_000.0
    }

    /**
     * Gets the minimum time for a label in milliseconds.
     */
    fun minMs(label: String): Double {
        val times = measurements[label] ?: return 0.0
        return (times.minOrNull() ?: 0L) / 1_000_000.0
    }

    /**
     * Gets the maximum time for a label in milliseconds.
     */
    fun maxMs(label: String): Double {
        val times = measurements[label] ?: return 0.0
        return (times.maxOrNull() ?: 0L) / 1_000_000.0
    }

    /**
     * Gets the count of measurements for a label.
     */
    fun count(label: String): Int = measurements[label]?.size ?: 0

    /**
     * Generates a formatted report of all metrics.
     */
    fun report(): String = buildString {
        appendLine("=== Performance Report: $name ===")
        appendLine()

        appendLine("Memory:")
        appendLine("  Start heap:  ${formatBytes(startHeapBytes)}")
        appendLine("  End heap:    ${formatBytes(endHeapBytes)}")
        appendLine("  Growth:      ${formatBytes(heapGrowthBytes())}")
        appendLine()

        appendLine("Timings:")
        measurements.keys.sorted().forEach { label ->
            val count = count(label)
            val total = totalMs(label)
            val avg = averageMs(label)
            val min = minMs(label)
            val max = maxMs(label)

            appendLine("  $label:")
            appendLine("    Count: ${decimalFormat.format(count)}")
            appendLine("    Total: ${formatMs(total)}")
            appendLine("    Avg:   ${formatMs(avg)}")
            appendLine("    Min:   ${formatMs(min)}")
            appendLine("    Max:   ${formatMs(max)}")
        }
    }

    /**
     * Asserts that a metric is below a threshold.
     */
    fun assertBelowMs(label: String, thresholdMs: Double, message: String = "") {
        val actual = totalMs(label)
        check(actual < thresholdMs) {
            "${message.ifEmpty { label }} took ${formatMs(actual)}, exceeds threshold of ${formatMs(thresholdMs)}"
        }
    }

    /**
     * Asserts that bytes per record is below a threshold.
     */
    fun assertBytesPerRecordBelow(recordCount: Int, thresholdBytes: Int, message: String = "") {
        val actual = bytesPerRecord(recordCount)
        check(actual < thresholdBytes) {
            "${message.ifEmpty { "Memory per record" }}: ${memoryFormat.format(actual)} bytes exceeds threshold of $thresholdBytes bytes"
        }
    }

    private fun formatMs(ms: Double): String = "${memoryFormat.format(ms)} ms"

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "${memoryFormat.format(bytes / 1_073_741_824.0)} GB"
            bytes >= 1_048_576 -> "${memoryFormat.format(bytes / 1_048_576.0)} MB"
            bytes >= 1024 -> "${memoryFormat.format(bytes / 1024.0)} KB"
            else -> "$bytes bytes"
        }
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun forceGc() {
        repeat(3) {
            System.gc()
            Thread.sleep(50)
        }
    }

    companion object {
        /**
         * Runs a warmup phase to trigger JIT compilation.
         */
        inline fun warmup(iterations: Int = 5, block: () -> Unit) {
            repeat(iterations) { block() }
        }
    }
}
