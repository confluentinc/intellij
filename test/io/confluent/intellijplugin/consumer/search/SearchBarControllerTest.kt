package io.confluent.intellijplugin.consumer.search

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.table.filters.TableFilterHeader
import io.confluent.intellijplugin.core.table.renderers.DateRenderer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Date
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
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
        private lateinit var table: JTable
        private lateinit var filterHeader: TableFilterHeader
        private lateinit var controller: SearchBarController

        @BeforeEach
        fun setUp() {
            SwingUtilities.invokeAndWait {
                disposable = Disposer.newDisposable("SearchBarControllerTest")
                val model = object : DefaultTableModel(
                    arrayOf(
                        arrayOf<Any?>("topicA", Date(0), "k1", "value1", 0, 100L),
                        arrayOf<Any?>(
                            "topicB", Date(0), "k2",
                            "{\n  \"nested\": \"value with {braces}\"\n}",
                            1, 200L,
                        ),
                        arrayOf<Any?>("topicA", Date(0), "k3", "plain text", 0, 300L),
                    ),
                    arrayOf("Topic", "Timestamp", "Key", "Value", "Partition", "Offset"),
                ) {
                    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
                        1 -> Date::class.java
                        4 -> Int::class.java
                        5 -> Long::class.java
                        else -> String::class.java
                    }
                }
                table = JTable(model).apply { rowSorter = TableRowSorter(model) }
                filterHeader = TableFilterHeader(table).apply { externalFilterMode = true }
                controller = SearchBarController(disposable, table, filterHeader, isProducer = false)
            }
        }

        @AfterEach
        fun tearDown() {
            SwingUtilities.invokeAndWait { Disposer.dispose(disposable) }
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
            setSearchAndFlush("{[(+*.?^\$|")
            // No exception from either regex compilation or the sort pass = pass.
        }

        @Test
        fun `free-text search matches within multi-line value`() {
            setSearchAndFlush("nested")
            assertEquals(1, visibleRowCount())
        }

        @Test
        fun `blank search clears the row filter`() {
            setSearchAndFlush("topicA")
            assertEquals(2, visibleRowCount())

            setSearchAndFlush("")
            val sorter = table.rowSorter as TableRowSorter<*>
            assertNull(sorter.rowFilter)
            assertEquals(3, visibleRowCount())
        }

        @Test
        fun `column-qualified search bar text populates column editor`() {
            setSearchAndFlush("topic:topicA")
            val topicEditor = filterHeader.columnsController!!.first { it?.modelIndex == 0 }!!
            assertEquals("topicA", topicEditor.text)
            assertEquals(2, visibleRowCount())
        }

        @Test
        fun `editing a column editor rebuilds search bar text without looping`() {
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
            // renderer's format ("2026-05-01 14:23:45"). Replace the model with rows whose timestamps
            // span two distinct dates and assert the rendered substring matches.
            SwingUtilities.invokeAndWait {
                val day1 = DateRenderer.df.parse("2026-05-01 12:00:00")
                val day2 = DateRenderer.df.parse("2026-06-15 09:30:00")
                val model = object : DefaultTableModel(
                    arrayOf(
                        arrayOf<Any?>("topicA", day1, "k1", "v1", 0, 1L),
                        arrayOf<Any?>("topicB", day1, "k2", "v2", 0, 2L),
                        arrayOf<Any?>("topicC", day2, "k3", "v3", 0, 3L),
                    ),
                    arrayOf("Topic", "Timestamp", "Key", "Value", "Partition", "Offset"),
                ) {
                    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
                        1 -> Date::class.java
                        4 -> Int::class.java
                        5 -> Long::class.java
                        else -> String::class.java
                    }
                }
                table.model = model
                table.rowSorter = TableRowSorter(model)
            }

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
            SwingUtilities.invokeAndWait {
                val day1 = DateRenderer.df.parse("2026-05-01 12:00:00")
                val day2 = DateRenderer.df.parse("2026-06-15 09:30:00")
                val model = object : DefaultTableModel(
                    arrayOf(
                        arrayOf<Any?>("topicA", day1, "k1", "v1", 0, 1L),
                        arrayOf<Any?>("topicB", day2, "k2", "v2", 0, 2L),
                    ),
                    arrayOf("Topic", "Timestamp", "Key", "Value", "Partition", "Offset"),
                ) {
                    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
                        1 -> Date::class.java
                        4 -> Int::class.java
                        5 -> Long::class.java
                        else -> String::class.java
                    }
                }
                table.model = model
                table.rowSorter = TableRowSorter(model)
            }

            setSearchAndFlush("timestamp:2026-05")
            assertEquals(1, visibleRowCount())
        }

        @Test
        fun `editor listeners are re-attached after columnsController is recreated`() {
            // Force columnsController recreation by swapping the table model.
            SwingUtilities.invokeAndWait {
                val model = object : DefaultTableModel(
                    arrayOf(arrayOf<Any?>("freshTopic", Date(0), "nk", "nv", 9, 900L)),
                    arrayOf("Topic", "Timestamp", "Key", "Value", "Partition", "Offset"),
                ) {
                    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
                        1 -> Date::class.java
                        4 -> Int::class.java
                        5 -> Long::class.java
                        else -> String::class.java
                    }
                }
                table.model = model
                table.rowSorter = TableRowSorter(model)
            }
            // Type into the *new* topic editor — must still push through to the search bar.
            setEditorAndFlush(modelIndex = 0, text = "freshTopic")
            assertEquals("topic:freshTopic", controller.searchField.text)
        }
    }
}
