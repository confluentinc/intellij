package io.confluent.intellijplugin.benchmark

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.core.table.MaterialTable
import io.confluent.intellijplugin.core.table.MaterialTableUtils
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.util.Date

/**
 * THROWAWAY benchmark — lives only on the `ncothren/perf-list-table-model-benchmark` branch and is
 * NOT meant to be merged to main. It exists to inform the 10k-vs-50k default buffer-capacity
 * decision for the consumer message viewer.
 *
 * Gated behind a system property so a normal `./gradlew test` skips it entirely. Run with:
 *
 *   ./gradlew test --tests "*ListTableModelBenchmark" -Dbenchmark.listTableModel=true --info
 *
 * (The `test {}` block in build.gradle.kts forwards the property to the forked JVM and bumps
 * -Xmx to 3g for the 50k x 16KB cell.)
 *
 * Two layers:
 *  1. Model-only — retained heap once the buffer is full, plus a fill-throughput proxy. No UI.
 *  2. Real table + sorter — per-flush EDT busy time (p50/p99) in wrapped steady state, with the
 *     production `TableRowSorter` (sortsOnUpdates=true) attached via [MaterialTableUtils.setupSorters].
 *
 * Honest limitation: layer 2 measures the EDT cost of `flushPendingAdds` + the *synchronous*
 * RowSorter maintenance it triggers. Async `repaint()` is coalesced by the RepaintManager and is
 * NOT captured — so these are sorter/model costs, not pixel-paint costs. That's fine for a relative
 * 10k-vs-50k comparison. Numbers are approximate (especially heap); read them as orders of magnitude.
 */
@TestApplication
@EnabledIfSystemProperty(named = "benchmark.listTableModel", matches = "true")
class ListTableModelBenchmark {

    private val capacities = listOf(10_000, 50_000)

    /** Approx retained bytes per record value (ASCII => ~1 byte/char with compact strings). */
    private val valueSizes = linkedMapOf("256B" to 256, "2KB" to 2_048, "16KB" to 16_384)

    private val columnNames = listOf("Topic", "Timestamp", "Key", "Value", "Partition", "Offset")

    private fun newModel(capacity: Int): ListTableModel<KafkaRecord> =
        ListTableModel(capacity, columnNames) { r, c ->
            when (c) {
                0 -> r.topic
                1 -> Date(r.timestamp)
                2 -> r.keyText
                3 -> r.valueText
                4 -> r.partition
                else -> r.offset
            }
        }

    /** A synthetic record whose retained text (`keyText`/`valueText`) is ~[valueBytes] for the value. */
    private fun makeRecord(valueBytes: Int, i: Int): KafkaRecord {
        // Start each value with the index so the 50k records hold distinct char[] backing arrays
        // (defeats any accidental string dedup) while staying ~valueBytes long.
        val prefix = i.toString()
        val value = prefix + "v".repeat((valueBytes - prefix.length).coerceAtLeast(0))
        return KafkaRecord(
            keyType = KafkaFieldType.STRING,
            valueType = KafkaFieldType.STRING,
            error = null,
            key = "k%010d".format(i),
            value = value,
            topic = "bench-topic",
            partition = i % 8,
            offset = i.toLong(),
            duration = -1,
            timestamp = BASE_TS + i,
            keySize = 16,
            valueSize = valueBytes,
            headers = emptyList(),
            keyFormat = KafkaRegistryFormat.UNKNOWN,
            valueFormat = KafkaRegistryFormat.UNKNOWN,
        )
    }

    private fun settledUsedBytes(): Long {
        val rt = Runtime.getRuntime()
        repeat(4) { System.gc(); Thread.sleep(60) }
        return rt.totalMemory() - rt.freeMemory()
    }

    // ---- Layer 1: model-only heap + fill throughput ---------------------------------------------

    @Test
    fun `report capacity benchmark`() {
        println("\n==================== ListTableModel capacity benchmark ====================")
        println("Heap numbers are approximate (gc + Runtime delta). Value size ~= ASCII chars.\n")

        // ---- Heap matrix -------------------------------------------------------------------------
        println("--- Layer 1: retained heap once buffer full (MB) ---")
        printHeader()
        for (cap in capacities) {
            val cells = valueSizes.values.map { bytes -> "%8.1f".format(measureRetainedMb(cap, bytes)) }
            printRow(cap, cells)
        }

        // ---- Layer 2: real table flush latency ---------------------------------------------------
        println("\n--- Layer 2: steady-state flush EDT time, p50 / p99 ms (batch=$BATCH, ${MEASURED} flushes) ---")
        printHeader()
        for (cap in capacities) {
            val cells = valueSizes.values.map { bytes ->
                val (p50, p99) = measureFlushLatencyMs(cap, bytes)
                "%4.2f/%5.2f".format(p50, p99)
            }
            printRow(cap, cells)
        }

        val analytic = capacities.joinToString("  ") { "$it@10k/s=${"%.1f".format(it / 10_000.0)}s" }
        println("\nTime-to-first-wrap is analytic = capacity / throughput. e.g. $analytic")
        println("===========================================================================\n")
    }

    private fun measureRetainedMb(capacity: Int, valueBytes: Int): Double {
        val before = settledUsedBytes()
        val model = newModel(capacity)
        model.replaceAll(List(capacity) { makeRecord(valueBytes, it) })  // synchronous, no EDT
        val after = settledUsedBytes()
        // Keep `model` live across the measurement so its buffer isn't collected before `after`.
        check(model.rowCount == capacity) { "fill mismatch: ${model.rowCount} != $capacity" }
        return (after - before) / (1024.0 * 1024.0)
    }

    private fun measureFlushLatencyMs(capacity: Int, valueBytes: Int): Pair<Double, Double> {
        val app = ApplicationManager.getApplication()
        val model = newModel(capacity)
        lateinit var table: MaterialTable
        app.invokeAndWait {
            // Pre-fill to capacity so every measured flush is a wrap (sliding-window delete+insert).
            model.replaceAll(List(capacity) { makeRecord(valueBytes, it) })
            table = MaterialTable(model, model.columnModel)
            MaterialTableUtils.setupSorters(table)  // production TableRowSorter, sortsOnUpdates=true
        }

        val samples = LongArray(MEASURED)
        var idx = capacity
        try {
            repeat(WARMUP + MEASURED) { iter ->
                val batch = ArrayList<KafkaRecord>(BATCH).apply { repeat(BATCH) { add(makeRecord(valueBytes, idx++)) } }
                app.invokeAndWait { model.addBatch(batch) }  // queues a coalesced flush via invokeLater
                val t0 = System.nanoTime()
                app.invokeAndWait { }                        // returns once the queued flush has drained
                if (iter >= WARMUP) samples[iter - WARMUP] = System.nanoTime() - t0
            }
        } finally {
            app.invokeAndWait { Disposer.dispose(table) }
        }

        samples.sort()
        val p50 = samples[samples.size / 2] / 1_000_000.0
        val p99 = samples[(samples.size * 99 / 100).coerceAtMost(samples.size - 1)] / 1_000_000.0
        return p50 to p99
    }

    private fun printHeader() {
        val cols = valueSizes.keys.joinToString("") { "%9s".format(it) }
        println("%8s%s".format("cap", cols))
    }

    private fun printRow(capacity: Int, cells: List<String>) {
        println("%8s%s".format("%,d".format(capacity), cells.joinToString("") { " %s".format(it) }))
    }

    private companion object {
        const val BASE_TS = 1_700_000_000_000L
        const val BATCH = 200      // records per coalesced flush
        const val WARMUP = 100     // flushes discarded before measuring
        const val MEASURED = 500   // flushes timed for percentiles
    }
}
