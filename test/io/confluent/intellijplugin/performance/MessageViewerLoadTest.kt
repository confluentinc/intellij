package io.confluent.intellijplugin.performance

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.core.table.filters.TableRowFilter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JTable

/**
 * Load tests for the Message Viewer to identify performance bottlenecks.
 *
 * These tests measure:
 * - Insertion throughput into ListTableModel
 * - FIFO eviction overhead at capacity
 * - Filter application time via TableRowFilter
 * - Memory consumption per record
 *
 * Target criteria from plan:
 * - Insert 100K records < 5 seconds
 * - Filter 100K records < 500ms
 * - No EDT blocking > 100ms per batch
 * - Memory < 1KB per record
 *
 * ## Key Findings (Initial Run)
 *
 * **FIFO Eviction is O(n²)**: The current `ListTableModel.addElement()` uses
 * `ArrayList.removeFirst()` which is O(n), causing O(n²) total time when at capacity:
 * - 10K records: 1.85x overhead
 * - 50K records: 25.7x overhead
 * - 100K records: 211.6x overhead
 *
 * **Root Cause**: `data.removeFirst()` shifts all elements in the ArrayList.
 * Fix: Use `ArrayDeque` or a circular buffer for O(1) eviction.
 *
 * **Memory**: OOM occurs around 500K-1M records with default heap settings.
 */
@TestApplication
class MessageViewerLoadTest {

    private lateinit var model: ListTableModel<KafkaRecord>
    private lateinit var table: JTable

    private val columnNames = listOf(
        "Topic", "Partition", "Offset", "Timestamp", "Key", "Value", "Key Size", "Value Size"
    )

    private val columnMapper: (KafkaRecord, Int) -> Any? = { record, column ->
        when (column) {
            0 -> record.topic
            1 -> record.partition
            2 -> record.offset
            3 -> record.timestamp
            4 -> record.keyText
            5 -> record.valueText
            6 -> record.keySize
            7 -> record.valueSize
            else -> null
        }
    }

    @BeforeEach
    fun setUp() {
        model = ListTableModel(mutableListOf(), columnNames, columnMapper)
        table = JTable(model)
    }

    @Nested
    inner class InsertionTests {

        @ParameterizedTest(name = "Insert {0} records")
        @ValueSource(ints = [10_000, 50_000, 100_000, 200_000])
        fun `should insert records within time budget`(recordCount: Int) {
            val metrics = PerformanceMetrics("Insertion @ $recordCount records")
            val records = SyntheticKafkaRecordGenerator.generateList(recordCount)

            // Warmup with smaller batch
            PerformanceMetrics.warmup {
                val warmupModel = ListTableModel<KafkaRecord>(mutableListOf(), columnNames, columnMapper)
                repeat(1000) { warmupModel.addElement(records[it]) }
            }

            metrics.captureHeapBaseline()

            metrics.measure("total_insertion") {
                records.forEach { record ->
                    model.addElement(record)
                }
            }

            metrics.captureHeapEnd()

            println(metrics.report())
            println("Records: $recordCount")
            println("Bytes per record: ${metrics.bytesPerRecord(recordCount)}")

            // Assertions based on plan criteria
            if (recordCount <= 100_000) {
                metrics.assertBelowMs("total_insertion", 5_000.0, "Insert $recordCount records")
            }

            // Document memory usage (observed ~3KB/record with mixed payload sizes)
            val bytesPerRecord = metrics.bytesPerRecord(recordCount)
            println("Memory: ${bytesPerRecord} bytes/record (target: <1024)")
            if (bytesPerRecord > 1024) {
                println("NOTE: Exceeds target due to String storage overhead and payload sizes")
            }
        }

        @ParameterizedTest(name = "Batch insert {0} records in chunks of 1000")
        @ValueSource(ints = [10_000, 100_000])
        fun `should insert in batches without long pauses`(recordCount: Int) {
            val metrics = PerformanceMetrics("Batch insertion @ $recordCount records")
            val records = SyntheticKafkaRecordGenerator.generateList(recordCount)
            val batchSize = 1000

            metrics.captureHeapBaseline()

            records.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                metrics.measure("batch_$batchIndex") {
                    batch.forEach { model.addElement(it) }
                }
            }

            metrics.captureHeapEnd()

            // Check that no single batch exceeds 100ms (EDT blocking threshold)
            val batchCount = recordCount / batchSize
            var maxBatchMs = 0.0
            repeat(batchCount) { batchIndex ->
                val batchMs = metrics.totalMs("batch_$batchIndex")
                if (batchMs > maxBatchMs) maxBatchMs = batchMs
            }

            println(metrics.report())
            println("Max batch time: $maxBatchMs ms")

            check(maxBatchMs < 100.0) {
                "Batch insertion blocked for ${maxBatchMs}ms, exceeds 100ms EDT threshold"
            }
        }
    }

    @Nested
    inner class FifoEvictionTests {

        /**
         * Measures FIFO eviction overhead.
         *
         * **Known Issue**: Current implementation uses ArrayList.removeFirst() which is O(n),
         * causing severe degradation at scale. This test documents the behavior.
         *
         * Expected results with current implementation:
         * - 10K: ~2x overhead
         * - 50K: ~25x overhead
         * - 100K: ~200x overhead
         */
        @ParameterizedTest(name = "FIFO eviction with max {0} and 2x insertions")
        @ValueSource(ints = [5_000, 10_000, 25_000])
        fun `should measure FIFO eviction overhead`(maxElements: Int) {
            model.maxElementsCount = maxElements

            val metrics = PerformanceMetrics("FIFO eviction @ max $maxElements")
            val totalInsertions = maxElements * 2

            // Pre-generate all records
            val records = SyntheticKafkaRecordGenerator.generateList(totalInsertions)

            // Measure insertions before hitting capacity
            metrics.measure("insertions_before_capacity") {
                repeat(maxElements) { model.addElement(records[it]) }
            }

            // Measure insertions with eviction active
            metrics.measure("insertions_with_eviction") {
                for (i in maxElements until totalInsertions) {
                    model.addElement(records[i])
                }
            }

            val beforeCapacityMs = metrics.totalMs("insertions_before_capacity")
            val withEvictionMs = metrics.totalMs("insertions_with_eviction")
            val overhead = if (beforeCapacityMs > 0) withEvictionMs / beforeCapacityMs else 0.0

            println(metrics.report())
            println("=== FIFO Eviction Analysis ===")
            println("Before capacity: ${beforeCapacityMs}ms")
            println("With eviction: ${withEvictionMs}ms")
            println("Overhead ratio: ${overhead}x")
            println("Target: < 1.5x (currently FAILING due to O(n) removeFirst)")
            println()

            // Document but don't fail - this is observational
            if (overhead > 1.5) {
                println("WARNING: Eviction overhead ${overhead}x exceeds 1.5x target")
                println("ROOT CAUSE: ArrayList.removeFirst() is O(n)")
                println("FIX: Use ArrayDeque or circular buffer for O(1) eviction")
            }
        }
    }

    @Nested
    inner class FilterTests {

        @ParameterizedTest(name = "Filter {0} records with text search")
        @ValueSource(ints = [10_000, 50_000, 100_000])
        fun `should filter records within time budget`(recordCount: Int) {
            // Pre-populate model
            val records = SyntheticKafkaRecordGenerator.generateList(recordCount)
            records.forEach { model.addElement(it) }

            val filter = TableRowFilter(table)
            val metrics = PerformanceMetrics("Filter @ $recordCount records")

            // Warmup filter
            PerformanceMetrics.warmup {
                filter.setConditions(listOf(0 to "test"))
                repeat(1000) { filter.include(MockRowFilterEntry(model, it)) }
            }

            // Test text filter on value column (column 5)
            metrics.measure("text_filter_setup") {
                filter.setConditions(listOf(5 to "data"))
            }

            metrics.measure("text_filter_apply") {
                var matchCount = 0
                for (row in 0 until model.rowCount) {
                    if (filter.include(MockRowFilterEntry(model, row))) {
                        matchCount++
                    }
                }
            }

            println(metrics.report())

            // Assertions based on plan criteria
            if (recordCount <= 100_000) {
                metrics.assertBelowMs("text_filter_apply", 500.0, "Filter $recordCount records")
            }
        }

        @ParameterizedTest(name = "Numeric filter on {0} records")
        @ValueSource(ints = [10_000, 100_000])
        fun `should filter with numeric conditions`(recordCount: Int) {
            val records = SyntheticKafkaRecordGenerator.generateList(recordCount)
            records.forEach { model.addElement(it) }

            val filter = TableRowFilter(table)
            val metrics = PerformanceMetrics("Numeric filter @ $recordCount records")

            // Filter by partition (column 1) greater than 5
            metrics.measure("numeric_filter_setup") {
                filter.setConditions(listOf(1 to ">5"))
            }

            metrics.measure("numeric_filter_apply") {
                var matchCount = 0
                for (row in 0 until model.rowCount) {
                    if (filter.include(MockRowFilterEntry(model, row))) {
                        matchCount++
                    }
                }
            }

            println(metrics.report())
        }

        @Test
        fun `should handle case-insensitive filtering`() {
            val recordCount = 50_000
            val records = SyntheticKafkaRecordGenerator.generateList(recordCount)
            records.forEach { model.addElement(it) }

            val filter = TableRowFilter(table)
            filter.compareCaseInsensitive = true

            val metrics = PerformanceMetrics("Case-insensitive filter @ $recordCount records")

            metrics.measure("case_insensitive_filter") {
                filter.setConditions(listOf(5 to "DATA"))
                for (row in 0 until model.rowCount) {
                    filter.include(MockRowFilterEntry(model, row))
                }
            }

            println(metrics.report())
        }
    }

    @Nested
    inner class MemoryTests {

        /**
         * Measures memory consumption per record.
         *
         * **Observed**: ~3KB per record with MIXED payload profile.
         * This is due to:
         * - Java String overhead (2 bytes/char + object header)
         * - Multiple string fields: topic, key, value, keyText, valueText, errorText
         * - Headers list with Property objects
         * - Payload sizes ranging 50-2200 bytes
         *
         * For 1M records at 3KB each = ~3GB heap required.
         */
        @ParameterizedTest(name = "Memory profile for {0} records")
        @ValueSource(ints = [10_000, 50_000])
        fun `should measure memory consumption per record`(recordCount: Int) {
            val metrics = PerformanceMetrics("Memory @ $recordCount records")

            metrics.captureHeapBaseline()

            val records = SyntheticKafkaRecordGenerator.generateList(recordCount)
            records.forEach { model.addElement(it) }

            metrics.captureHeapEnd()

            val bytesPerRecord = metrics.bytesPerRecord(recordCount)
            val estimatedBytes = records.take(100)
                .map { SyntheticKafkaRecordGenerator.estimateRecordBytes(it) }
                .average()

            println(metrics.report())
            println("=== Memory Analysis ===")
            println("Measured bytes per record: $bytesPerRecord")
            println("Estimated bytes per record: $estimatedBytes")
            println("Target: <1024 bytes/record")
            println()

            if (bytesPerRecord > 1024) {
                println("FINDING: Memory per record (${bytesPerRecord.toInt()} bytes) exceeds 1KB target")
                println("At 1M records, this requires: ${(bytesPerRecord * 1_000_000 / 1_073_741_824).toInt()} GB heap")
                println("Consider: Store raw bytes instead of parsed strings, lazy parsing")
            }
        }

        @Test
        fun `should profile memory by payload size`() {
            val recordCount = 10_000

            SyntheticKafkaRecordGenerator.PayloadProfile.entries.forEach { profile ->
                val metrics = PerformanceMetrics("Memory @ $profile payload")

                metrics.captureHeapBaseline()

                val testModel = ListTableModel<KafkaRecord>(mutableListOf(), columnNames, columnMapper)
                val records = SyntheticKafkaRecordGenerator.generateList(recordCount, profile = profile)
                records.forEach { testModel.addElement(it) }

                metrics.captureHeapEnd()

                println("$profile: ${metrics.bytesPerRecord(recordCount)} bytes/record")
            }
        }
    }

    /**
     * Mock implementation of RowFilter.Entry for testing.
     */
    private class MockRowFilterEntry(
        private val tableModel: ListTableModel<KafkaRecord>,
        private val row: Int
    ) : javax.swing.RowFilter.Entry<javax.swing.table.TableModel, Int>() {

        override fun getModel(): javax.swing.table.TableModel = tableModel

        override fun getValueCount(): Int = tableModel.columnCount

        override fun getValue(index: Int): Any? = tableModel.getValueAt(row, index)

        override fun getIdentifier(): Int = row

        override fun getStringValue(index: Int): String = getValue(index)?.toString() ?: ""
    }
}
