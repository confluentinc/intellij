package io.confluent.intellijplugin.consumer.search

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.editor.KafkaRecordsOutput
import io.confluent.intellijplugin.core.table.filters.TableFilterHeader
import io.confluent.intellijplugin.core.table.renderers.DateRenderer
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.TableRowSorter

class SearchBarControllerTest {

    @Nested
    inner class SearchKeyMap {

        @Test
        fun `consumer map has offset at index 5`() {
            val map = SearchBarController.searchKeyMap(isProducer = false)
            assertEquals(5, map["offset"])
            assertNull(map["duration"])
        }

        @Test
        fun `producer map has duration at index 5`() {
            val map = SearchBarController.searchKeyMap(isProducer = true)
            assertEquals(5, map["duration"])
            assertNull(map["offset"])
        }

        @Test
        fun `shared columns have same model indices across consumer and producer`() {
            val consumer = SearchBarController.searchKeyMap(isProducer = false)
            val producer = SearchBarController.searchKeyMap(isProducer = true)
            listOf("topic", "timestamp", "key", "value", "partition").forEach { col ->
                assertEquals(consumer[col], producer[col], "mismatch for column '$col'")
            }
        }

        @Test
        fun `all search keys map to expected model indices`() {
            val map = SearchBarController.searchKeyMap(isProducer = false)
            assertEquals(0, map["topic"])
            assertEquals(1, map["timestamp"])
            assertEquals(2, map["key"])
            assertEquals(3, map["value"])
            assertEquals(4, map["partition"])
            assertEquals(5, map["offset"])
        }
    }

    @Nested
    @TestApplication
    inner class FilterBehavior {

        private lateinit var disposable: Disposable
        private lateinit var output: KafkaRecordsOutput
        private lateinit var table: JTable
        private lateinit var filterHeader: TableFilterHeader
        private lateinit var controller: SearchBarController

        @BeforeEach
        fun setUp() {
            SwingUtilities.invokeAndWait {
                disposable = Disposer.newDisposable("SearchBarControllerTest")
                val project: Project = mock()
                // Drive the test through KafkaRecordsOutput's real outputModel so changes to its
                // column layout (count, ordering, classes) break this filter test rather than
                // silently diverging from production behavior.
                output = KafkaRecordsOutput(project, isProducer = false)
                Disposer.register(disposable, output)
                table = JTable(output.outputModel, output.outputModel.columnModel).apply {
                    rowSorter = TableRowSorter(model)
                }
                filterHeader = TableFilterHeader(table).apply { externalFilterMode = true }
                controller = SearchBarController(disposable, table, filterHeader, isProducer = false)
            }
        }

        @AfterEach
        fun tearDown() {
            SwingUtilities.invokeAndWait { Disposer.dispose(disposable) }
        }

        private fun record(
            topic: String,
            key: String,
            value: String,
            partition: Int,
            offset: Long,
            timestampMs: Long = 0L,
        ): KafkaRecord = KafkaRecord(
            keyType = KafkaFieldType.STRING,
            valueType = KafkaFieldType.STRING,
            error = null,
            key = key,
            value = value,
            topic = topic,
            partition = partition,
            offset = offset,
            duration = -1,
            timestamp = timestampMs,
            keySize = 0,
            valueSize = 0,
            headers = emptyList(),
            keyFormat = KafkaRegistryFormat.UNKNOWN,
            valueFormat = KafkaRegistryFormat.UNKNOWN,
        )

        private fun loadRows(records: List<KafkaRecord>) {
            // replace() is synchronous, so the model reflects these rows by the time it returns.
            SwingUtilities.invokeAndWait { output.replace(records) }
        }

        private fun setSearchAndFlush(text: String) {
            SwingUtilities.invokeAndWait { controller.searchField.text = text }
            controller.waitForPendingInTest()
        }

        private fun setEditorAndFlush(modelIndex: Int, text: String) {
            SwingUtilities.invokeAndWait {
                val editor = filterHeader.columnsController!!.first { it?.modelIndex == modelIndex }!!
                editor.text = text
            }
            controller.waitForPendingInTest()
        }

        private fun visibleRowCount(): Int = table.rowSorter.viewRowCount

        @Test
        fun `metacharacters in search bar do not throw`() {
            loadRows(listOf(record("topicA", "k1", "value1", 0, 100L)))
            setSearchAndFlush("{[(+*.?^\$|")
            // No exception from either regex compilation or the sort pass = pass.
        }

        @Test
        fun `free-text search matches within multi-line value`() {
            loadRows(
                listOf(
                    record("topicA", "k1", "value1", 0, 100L),
                    record("topicB", "k2", "{\n  \"nested\": \"value with {braces}\"\n}", 1, 200L),
                    record("topicA", "k3", "plain text", 0, 300L),
                )
            )
            setSearchAndFlush("nested")
            assertEquals(1, visibleRowCount())
        }

        @Test
        fun `blank search clears the row filter`() {
            loadRows(
                listOf(
                    record("topicA", "k1", "value1", 0, 100L),
                    record("topicB", "k2", "value2", 1, 200L),
                    record("topicA", "k3", "value3", 0, 300L),
                )
            )
            setSearchAndFlush("topicA")
            assertEquals(2, visibleRowCount())

            setSearchAndFlush("")
            val sorter = table.rowSorter as TableRowSorter<*>
            assertNull(sorter.rowFilter)
            assertEquals(3, visibleRowCount())
        }

        @Test
        fun `column-qualified search bar text populates column editor`() {
            loadRows(
                listOf(
                    record("topicA", "k1", "value1", 0, 100L),
                    record("topicB", "k2", "value2", 1, 200L),
                    record("topicA", "k3", "value3", 0, 300L),
                )
            )
            setSearchAndFlush("topic:topicA")
            val topicEditor = filterHeader.columnsController!!.first { it?.modelIndex == 0 }!!
            assertEquals("topicA", topicEditor.text)
            assertEquals(2, visibleRowCount())
        }

        @Test
        fun `editing a column editor rebuilds search bar text without looping`() {
            loadRows(
                listOf(
                    record("topicA", "k1", "value1", 0, 100L),
                    record("topicB", "k2", "value2", 1, 200L),
                )
            )
            setEditorAndFlush(modelIndex = 0, text = "topicB")
            // Search bar now mirrors the column editor — proves one pass of sync ran.
            assertEquals("topic:topicB", controller.searchField.text)
            assertEquals(1, visibleRowCount())
            // And after the sync, a second flush finds no pending work — proves no loop.
            controller.waitForPendingInTest()
            assertEquals("topic:topicB", controller.searchField.text)
        }

        @Test
        fun `free-text search matches Date column by its rendered yyyy-MM-dd HH-mm-ss format`() {
            // Reviewers reported that typing "-" or "05" against a Timestamp column dropped all rows
            // because the filter was reading Date.toString() ("Fri May 01 ... 2026") instead of the
            // renderer's format ("2026-05-01 14:23:45"). Load rows whose timestamps span two distinct
            // dates and assert the rendered substring matches.
            val day1 = DateRenderer.df.parse("2026-05-01 12:00:00").time
            val day2 = DateRenderer.df.parse("2026-06-15 09:30:00").time
            loadRows(
                listOf(
                    record("topicA", "k1", "v1", 0, 1L, timestampMs = day1),
                    record("topicB", "k2", "v2", 0, 2L, timestampMs = day1),
                    record("topicC", "k3", "v3", 0, 3L, timestampMs = day2),
                )
            )

            // "-" appears in every rendered date but in no Date.toString() — must match all rows.
            setSearchAndFlush("-")
            assertEquals(3, visibleRowCount())

            // "05" matches only the May rows under the rendered format; under Date.toString()
            // ("Fri May 01") it would match nothing.
            setSearchAndFlush("05")
            assertEquals(2, visibleRowCount())

            // Full ISO-like date prefix matches only its day's rows.
            setSearchAndFlush("2026-06-15")
            assertEquals(1, visibleRowCount())
        }

        @Test
        fun `column-qualified timestamp search matches against rendered date format`() {
            val day1 = DateRenderer.df.parse("2026-05-01 12:00:00").time
            val day2 = DateRenderer.df.parse("2026-06-15 09:30:00").time
            loadRows(
                listOf(
                    record("topicA", "k1", "v1", 0, 1L, timestampMs = day1),
                    record("topicB", "k2", "v2", 0, 2L, timestampMs = day2),
                )
            )

            setSearchAndFlush("timestamp:2026-05")
            assertEquals(1, visibleRowCount())
        }

        @Test
        fun `free-text search produces a BitSet-backed RowFilter`() {
            loadRows(
                listOf(
                    record("topicA", "k1", "value1", 0, 100L),
                    record("topicB", "k2", "{\"nested\":\"value\"}", 1, 200L),
                    record("topicA", "k3", "plain text", 0, 300L),
                )
            )
            setSearchAndFlush("plain")
            assertEquals(1, visibleRowCount())
            // Cache snapshot for the term is recorded so a repeat doesn't rebuild.
            assertEquals("plain", controller.freeTextSnapshotForTest())
        }

        @Test
        fun `column filter still applies when free-text BitSet is also active`() {
            loadRows(
                listOf(
                    record("topicA", "k1", "value1", 0, 100L),
                    record("topicA", "k2", "value2", 0, 200L),
                    record("topicB", "k3", "value1", 0, 300L),
                )
            )
            setSearchAndFlush("topic:topicA value:value1")
            assertEquals(1, visibleRowCount(), "Only row matching both column filters should remain")
        }

        @Test
        fun `cached BitSet is reused when only column filters change`() {
            loadRows(
                listOf(
                    record("topicA", "k1", "plain text", 0, 100L),
                    record("topicB", "k2", "plain", 0, 200L),
                )
            )
            // First search builds the BitSet for "plain".
            setSearchAndFlush("plain")
            val firstBits = controller.freeTextBitSetForTest()
            assertNotNull(firstBits)

            // Same free-text plus a new column filter — parsed != lastApplied so applyUnifiedFilter
            // runs to completion, but the free-text branch must take the cache path (no rebuild).
            setSearchAndFlush("topic:topicA plain")
            val secondBits = controller.freeTextBitSetForTest()

            assertSame(firstBits, secondBits, "Cache path must reuse the BitSet instance")
        }

        @Test
        fun `editor listeners are re-attached after columnsController is recreated`() {
            loadRows(listOf(record("staleTopic", "sk", "sv", 0, 0L)))
            // Force columnsController recreation by reloading the rows under a fresh sorter.
            // (The header rebuilds its editors when the sorter changes.)
            SwingUtilities.invokeAndWait {
                table.rowSorter = TableRowSorter(output.outputModel)
            }
            loadRows(listOf(record("freshTopic", "nk", "nv", 9, 900L)))
            // Type into the *new* topic editor — must still push through to the search bar.
            setEditorAndFlush(modelIndex = 0, text = "freshTopic")
            assertEquals("topic:freshTopic", controller.searchField.text)
        }
    }
}
