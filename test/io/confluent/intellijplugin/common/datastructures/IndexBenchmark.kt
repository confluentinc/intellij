package io.confluent.intellijplugin.common.datastructures

import io.confluent.intellijplugin.performance.PerformanceMetrics
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Benchmarks comparing the new data structures against baseline implementations.
 *
 * ## Success Criteria (from plan)
 * - SkipList range query 50x faster than LinkedList filter at 1M records
 * - BitSet intersection < 5ms for two filters at 1M records
 * - Memory overhead < 20% over baseline
 *
 * ## Key Comparisons
 *
 * | Operation | New Structure | Baseline |
 * |-----------|---------------|----------|
 * | Range query | TimestampIndex (O(log n + k)) | LinkedList filter (O(n)) |
 * | Filter intersection | FilterBitSet.intersect (O(n/64)) | Re-evaluate predicate (O(n)) |
 * | FIFO eviction | MessageBuffer.add (O(1)) | ArrayList.removeFirst (O(n)) |
 */
class IndexBenchmark {

    /**
     * Simple record to simulate KafkaRecord fields for realistic filtering.
     */
    private data class MockRecord(
        val topic: String,
        val partition: Int,
        val offset: Long,
        val key: String?,
        val value: String
    ) {
        /**
         * Simulates TableModel.getValueAt() - returns Any? requiring toString() for filtering.
         */
        fun getValueAt(columnIndex: Int): Any? = when (columnIndex) {
            0 -> topic
            1 -> partition
            2 -> offset
            3 -> key
            4 -> value
            else -> null
        }
    }

    @Nested
    inner class MessageBufferVsArrayList {

        /**
         * Compares O(1) eviction in MessageBuffer vs O(n) in ArrayList.
         */
        @ParameterizedTest(name = "FIFO eviction comparison at {0} elements")
        @ValueSource(ints = [1_000, 5_000, 10_000, 25_000])
        fun `should compare eviction performance`(capacity: Int) {
            val totalInsertions = capacity * 2
            val metrics = PerformanceMetrics("Eviction @ $capacity")

            // Warmup
            PerformanceMetrics.warmup {
                val warmup = MessageBuffer<Int>(100)
                repeat(200) { warmup.add(it) }
            }

            // Benchmark MessageBuffer (ArrayDeque-based)
            val buffer = MessageBuffer<Int>(capacity)
            metrics.measure("MessageBuffer") {
                repeat(totalInsertions) { buffer.add(it) }
            }

            // Benchmark ArrayList with removeFirst
            val arrayList = ArrayList<Int>(capacity)
            metrics.measure("ArrayList") {
                repeat(totalInsertions) { i ->
                    if (arrayList.size >= capacity) {
                        arrayList.removeFirst()
                    }
                    arrayList.add(i)
                }
            }

            val bufferMs = metrics.totalMs("MessageBuffer")
            val arrayListMs = metrics.totalMs("ArrayList")
            val speedup = arrayListMs / bufferMs

            println("=== FIFO Eviction @ $capacity elements ===")
            println("MessageBuffer (ArrayDeque): ${bufferMs}ms")
            println("ArrayList.removeFirst:      ${arrayListMs}ms")
            println("Speedup: ${speedup}x")
            println()

            // MessageBuffer should be significantly faster
            if (capacity >= 5_000) {
                println(if (speedup > 2) "PASS: MessageBuffer is ${speedup}x faster" else "WARN: Speedup only ${speedup}x")
            }
        }
    }

    @Nested
    inner class TimestampIndexVsLinearScan {

        /**
         * Compares O(log n + k) range query vs O(n) linear scan.
         */
        @ParameterizedTest(name = "Range query comparison at {0} elements")
        @ValueSource(ints = [10_000, 100_000, 500_000, 1_000_000])
        fun `should compare range query performance`(size: Int) {
            val metrics = PerformanceMetrics("Range query @ $size")

            // Setup: create index and list with same data
            val index = TimestampIndex()
            val list = ArrayList<Pair<Long, Int>>(size)

            for (i in 0 until size) {
                val timestamp = i.toLong() * 1000 // 0, 1000, 2000, ...
                index.insert(timestamp, i)
                list.add(timestamp to i)
            }

            // Query parameters: find ~1000 elements in the middle
            val queryStart = (size / 2) * 1000L
            val queryEnd = queryStart + 1000 * 1000L // 1000 elements
            val expectedCount = 1000

            // Warmup
            PerformanceMetrics.warmup {
                index.rangeQuery(queryStart, queryEnd)
                list.filter { it.first in queryStart until queryEnd }
            }

            // Benchmark TimestampIndex
            var indexResultCount = 0
            metrics.measure("TimestampIndex") {
                repeat(10) {
                    indexResultCount = index.rangeQuery(queryStart, queryEnd).size
                }
            }

            // Benchmark linear scan
            var listResultCount = 0
            metrics.measure("LinearScan") {
                repeat(10) {
                    listResultCount = list.count { it.first in queryStart until queryEnd }
                }
            }

            val indexMs = metrics.totalMs("TimestampIndex")
            val listMs = metrics.totalMs("LinearScan")
            val speedup = if (indexMs > 0) listMs / indexMs else 0.0

            println("=== Range Query @ $size elements (find $expectedCount) ===")
            println("TimestampIndex: ${indexMs}ms (found $indexResultCount per iteration)")
            println("Linear scan:    ${listMs}ms (found $listResultCount per iteration)")
            println("Speedup: ${speedup}x")
            println()

            // Verify correctness (count is from last iteration, not accumulated)
            check(indexResultCount == expectedCount) { "Index found wrong count: $indexResultCount, expected $expectedCount" }
            check(listResultCount == expectedCount) { "Linear scan found wrong count: $listResultCount, expected $expectedCount" }

            // Success criteria: 50x faster at 1M
            if (size >= 1_000_000) {
                println(if (speedup >= 50) "PASS: ${speedup}x speedup meets 50x target" else "WARN: ${speedup}x speedup below 50x target")
            }
        }

        @Test
        fun `should measure memory overhead of TimestampIndex`() {
            val metrics = PerformanceMetrics("TimestampIndex memory")
            val size = 100_000

            metrics.captureHeapBaseline()

            val index = TimestampIndex()
            for (i in 0 until size) {
                index.insert(i.toLong(), i)
            }

            metrics.captureHeapEnd()

            val bytesPerEntry = metrics.bytesPerRecord(size)
            println("=== TimestampIndex Memory ===")
            println("Entries: $size")
            println("Bytes per entry: $bytesPerEntry")
            println("Total: ${metrics.heapGrowthBytes() / 1024} KB")

            // ConcurrentSkipListMap overhead is ~50-60 bytes per entry
            println(if (bytesPerEntry < 80) "PASS: Memory overhead acceptable" else "WARN: High memory overhead")
        }
    }

    @Nested
    inner class FilterBitSetVsPredicate {

        /**
         * Simulates TableRowFilter behavior: getValueAt -> toString -> contains/regex.
         * This matches the actual implementation in TableRowFilter.kt.
         */
        private fun simulateTableRowFilter(
            records: List<MockRecord>,
            columnIndex: Int,
            searchText: String,
            caseInsensitive: Boolean = false
        ): Int {
            val regex = if (caseInsensitive) Regex("(?i).*$searchText.*") else null
            return records.count { record ->
                val value: Any? = record.getValueAt(columnIndex)
                if (caseInsensitive) {
                    value?.toString()?.matches(regex!!) == true
                } else {
                    value?.toString()?.contains(searchText) == true
                }
            }
        }

        /**
         * Compares BitSet intersection vs re-evaluating predicates using realistic
         * string-based filtering that matches TableRowFilter behavior.
         *
         * The baseline simulates what TableRowFilter.include() does:
         * 1. getValueAt(row, column) - field access
         * 2. toString() - string conversion
         * 3. contains() or regex match - string matching
         */
        @ParameterizedTest(name = "Filter intersection at {0} elements")
        @ValueSource(ints = [100_000, 500_000, 1_000_000])
        fun `should compare filter intersection performance`(size: Int) {
            val metrics = PerformanceMetrics("Filter intersection @ $size")

            // Generate realistic test data
            val records = List(size) { i ->
                MockRecord(
                    topic = if (i % 2 == 0) "orders-topic" else "users-topic",
                    partition = i % 10,
                    offset = i.toLong(),
                    key = "key-$i",
                    value = if (i % 3 == 0) "payload with error code" else "normal payload data"
                )
            }

            // Pre-compute BitSet filters (simulates cached filter results)
            val filter1 = FilterBitSet(size)
            val filter2 = FilterBitSet(size)
            records.forEachIndexed { index, record ->
                if (record.topic.contains("orders")) filter1.set(index)
                if (record.value.contains("error")) filter2.set(index)
            }

            // Warmup
            PerformanceMetrics.warmup {
                filter1.intersect(filter2)
                simulateTableRowFilter(records.take(1000), 0, "orders")
            }

            // Benchmark BitSet intersection (cached filter results)
            var bitsetCardinality = 0
            metrics.measure("BitSet.intersect") {
                repeat(10) {
                    bitsetCardinality = filter1.intersect(filter2).cardinality()
                }
            }

            // Benchmark TableRowFilter-style re-evaluation
            // This is what happens today: re-scan all rows for each filter combination
            var predicateCount = 0
            metrics.measure("TableRowFilter-style") {
                repeat(10) {
                    // Simulates applying two column filters and counting matches
                    // Uses getValueAt() -> toString() -> contains() like TableRowFilter.include()
                    predicateCount = records.count { record ->
                        record.getValueAt(0)?.toString()?.contains("orders") == true &&
                            record.getValueAt(4)?.toString()?.contains("error") == true
                    }
                }
            }

            val bitsetMs = metrics.totalMs("BitSet.intersect")
            val predicateMs = metrics.totalMs("TableRowFilter-style")
            val speedup = predicateMs / bitsetMs

            println("=== Filter Intersection @ $size elements ===")
            println("BitSet.intersect:       ${bitsetMs}ms (cardinality: ${bitsetCardinality / 10})")
            println("TableRowFilter-style:   ${predicateMs}ms (count: ${predicateCount / 10})")
            println("Speedup: ${speedup}x")
            println()

            // Success criteria: intersection < 5ms at 1M
            if (size >= 1_000_000) {
                val perIterationMs = bitsetMs / 10
                println(if (perIterationMs < 5) "PASS: ${perIterationMs}ms meets <5ms target" else "WARN: ${perIterationMs}ms exceeds 5ms target")
            }
        }

        /**
         * Additional benchmark: case-insensitive regex matching (worst case for TableRowFilter).
         */
        @ParameterizedTest(name = "Case-insensitive filter at {0} elements")
        @ValueSource(ints = [100_000, 500_000])
        fun `should compare case-insensitive filter performance`(size: Int) {
            val metrics = PerformanceMetrics("Case-insensitive filter @ $size")

            val records = List(size) { i ->
                MockRecord(
                    topic = "Topic-$i",
                    partition = i % 10,
                    offset = i.toLong(),
                    key = "Key-$i",
                    value = if (i % 4 == 0) "ERROR: Something failed" else "Info: Normal message"
                )
            }

            // Pre-compute BitSet filter
            val cachedFilter = FilterBitSet(size)
            val regex = Regex("(?i).*error.*")
            records.forEachIndexed { index, record ->
                if (record.value.matches(regex)) cachedFilter.set(index)
            }

            // Warmup
            PerformanceMetrics.warmup {
                cachedFilter.matchingIndices().take(100).toList()
                simulateTableRowFilter(records.take(1000), 4, "error", caseInsensitive = true)
            }

            // Benchmark: iterate cached BitSet
            var bitsetCount = 0
            metrics.measure("BitSet.matchingIndices") {
                repeat(10) {
                    bitsetCount = cachedFilter.matchingIndices().count()
                }
            }

            // Benchmark: re-evaluate regex on every row (TableRowFilter with compareCaseInsensitive=true)
            var regexCount = 0
            metrics.measure("Regex re-evaluation") {
                repeat(10) {
                    regexCount = simulateTableRowFilter(records, 4, "error", caseInsensitive = true)
                }
            }

            val bitsetMs = metrics.totalMs("BitSet.matchingIndices")
            val regexMs = metrics.totalMs("Regex re-evaluation")
            val speedup = regexMs / bitsetMs

            println("=== Case-Insensitive Filter @ $size elements ===")
            println("BitSet (cached):      ${bitsetMs}ms (count: ${bitsetCount / 10})")
            println("Regex re-evaluation:  ${regexMs}ms (count: ${regexCount / 10})")
            println("Speedup: ${speedup}x")
            println()
        }

        @Test
        fun `should measure BitSet memory at 1M capacity`() {
            val metrics = PerformanceMetrics("FilterBitSet memory")

            metrics.captureHeapBaseline()

            val bitset = FilterBitSet(1_000_000)
            // Set half the bits to trigger allocation
            for (i in 0 until 1_000_000 step 2) {
                bitset.set(i)
            }

            metrics.captureHeapEnd()

            val actualBytes = metrics.heapGrowthBytes()
            val expectedBytes = bitset.memoryUsageBytes()

            println("=== FilterBitSet Memory @ 1M capacity ===")
            println("Capacity: 1,000,000 bits")
            println("Estimated: ${expectedBytes / 1024} KB")
            println("Actual: ${actualBytes / 1024} KB")
            println("Target: ~125 KB")

            // Should be close to 125KB (1M bits / 8 = 125KB)
            println(if (actualBytes < 200_000) "PASS: Memory within expected range" else "WARN: Higher than expected memory")
        }
    }

    @Nested
    inner class CombinedOperations {

        /**
         * Simulates a realistic workflow: add messages, then filter by time range.
         */
        @Test
        fun `should benchmark realistic message viewer workflow`() {
            val messageCount = 100_000
            val metrics = PerformanceMetrics("Realistic workflow @ $messageCount messages")

            // Setup data structures
            val buffer = MessageBuffer<String>(messageCount)
            val timestampIndex = TimestampIndex()
            val filterCache = mutableMapOf<String, FilterBitSet>()

            // Simulate adding messages
            metrics.measure("add_messages") {
                repeat(messageCount) { i ->
                    val message = "Message $i with some content"
                    buffer.add(message)
                    timestampIndex.insert(i.toLong() * 100, i)
                }
            }

            // Simulate applying a text filter
            metrics.measure("apply_text_filter") {
                val textFilter = FilterBitSet(messageCount)
                buffer.asSequence().forEachIndexed { index, message ->
                    if (message.contains("content")) {
                        textFilter.set(index)
                    }
                }
                filterCache["text"] = textFilter
            }

            // Simulate time range filter using index
            metrics.measure("apply_time_filter") {
                val timeFilter = FilterBitSet(messageCount)
                val rangeStart = 25_000L * 100
                val rangeEnd = 75_000L * 100
                timestampIndex.rangeQuery(rangeStart, rangeEnd).forEach {
                    timeFilter.set(it)
                }
                filterCache["time"] = timeFilter
            }

            // Combine filters
            metrics.measure("combine_filters") {
                val combined = filterCache["text"]!!.intersect(filterCache["time"]!!)
                println("Combined filter matches: ${combined.cardinality()}")
            }

            println(metrics.report())
        }
    }
}
