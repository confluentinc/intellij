package io.confluent.intellijplugin.consumer.editor

import com.google.gson.JsonParser
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

@TestApplication
class ConsumerEditorUtilsTest {

    private fun createTableModel(
        columns: List<String>,
        rows: List<List<Any?>>
    ): ListTableModel<List<Any?>> = ListTableModel(
        rows.toMutableList(),
        columns
    ) { row, col -> row[col] }

    @Nested
    @DisplayName("parsePartitionsText")
    inner class ParsePartitionsText {

        @Test
        fun `should return empty list for null input`() {
            val result = ConsumerEditorUtils.parsePartitionsText(null)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list for blank input`() {
            val result = ConsumerEditorUtils.parsePartitionsText("")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should parse single partition number`() {
            val result = ConsumerEditorUtils.parsePartitionsText("3")
            assertEquals(listOf(3), result)
        }

        @Test
        fun `should parse comma-separated partition numbers`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0,1,2")
            assertEquals(listOf(0, 1, 2), result)
        }

        @Test
        fun `should parse range notation`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0-5")
            assertEquals(listOf(0, 1, 2, 3, 4, 5), result)
        }

        @Test
        fun `should parse mixed single and range notation`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0,2-4,7")
            assertEquals(listOf(0, 2, 3, 4, 7), result)
        }

        @Test
        fun `should ignore whitespace`() {
            val result = ConsumerEditorUtils.parsePartitionsText(" 0 , 2 - 4 , 7 ")
            assertEquals(listOf(0, 2, 3, 4, 7), result)
        }

        @Test
        fun `should ignore non-numeric entries`() {
            val result = ConsumerEditorUtils.parsePartitionsText("0,abc,2")
            assertEquals(listOf(0, 2), result)
        }

        @Test
        fun `should handle invalid range gracefully`() {
            val result = ConsumerEditorUtils.parsePartitionsText("a-b")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should handle single element range`() {
            val result = ConsumerEditorUtils.parsePartitionsText("3-3")
            assertEquals(listOf(3), result)
        }
    }

    @Nested
    @DisplayName("getStartWith")
    inner class GetStartWith {

        @Test
        fun `should create OFFSET start with positive offset`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.OFFSET, "100", null, null
            )
            assertEquals(ConsumerStartType.OFFSET, result.type)
            assertEquals(100L, result.offset)
            assertNull(result.time)
        }

        @Test
        fun `should create LATEST_OFFSET_MINUS_X with negated offset`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.LATEST_OFFSET_MINUS_X, "50", null, null
            )
            assertEquals(ConsumerStartType.LATEST_OFFSET_MINUS_X, result.type)
            assertEquals(-50L, result.offset)
            assertNull(result.time)
        }

        @Test
        fun `should create THE_BEGINNING with offset 0`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.THE_BEGINNING, "", null, null
            )
            assertEquals(ConsumerStartType.THE_BEGINNING, result.type)
            assertEquals(0L, result.offset)
            assertNull(result.time)
        }

        @Test
        fun `should create SPECIFIC_DATE with timestamp from Date`() {
            val date = Date(1700000000000L)
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.SPECIFIC_DATE, "", date, null
            )
            assertEquals(ConsumerStartType.SPECIFIC_DATE, result.type)
            assertEquals(1700000000000L, result.time)
            assertNull(result.offset)
        }

        @Test
        fun `should set null time for non-SPECIFIC_DATE types`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.NOW, "", null, null
            )
            assertEquals(ConsumerStartType.NOW, result.type)
            assertNull(result.time)
            assertNull(result.offset)
        }

        @Test
        fun `should pass through consumer group string`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.CONSUMER_GROUP, "", null, "my-group"
            )
            assertEquals(ConsumerStartType.CONSUMER_GROUP, result.type)
            assertEquals("my-group", result.consumerGroup)
        }

        @Test
        fun `should handle blank offset text as null offset`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.OFFSET, "", null, null
            )
            assertEquals(ConsumerStartType.OFFSET, result.type)
            assertNull(result.offset)
        }

        @Test
        fun `should handle non-numeric offset text as null`() {
            val result = ConsumerEditorUtils.getStartWith(
                ConsumerStartType.OFFSET, "abc", null, null
            )
            assertEquals(ConsumerStartType.OFFSET, result.type)
            assertNull(result.offset)
        }
    }

    @Nested
    @DisplayName("getTableContent dispatch")
    inner class GetTableContent {

        @Test
        fun `should delegate to JSON export when fileExtension is json`() {
            val model = createTableModel(listOf("Name"), listOf(listOf("Alice")))
            val result = ConsumerEditorUtils.getTableContent(model, "json")
            assertTrue(result.trimStart().startsWith("["), "Should produce JSON array output")
        }

        @Test
        fun `should delegate to TSV export when fileExtension is tsv`() {
            val model = createTableModel(listOf("A", "B"), listOf(listOf("1", "2")))
            val result = ConsumerEditorUtils.getTableContent(model, "tsv")
            val headerLine = result.lines().first()
            assertTrue(headerLine.contains("\t"), "TSV header should use tab separator")
            assertFalse(headerLine.contains(","), "TSV header should not use comma separator")
        }

        @Test
        fun `should delegate to CSV export for any other fileExtension`() {
            val model = createTableModel(listOf("A", "B"), listOf(listOf("1", "2")))
            val result = ConsumerEditorUtils.getTableContent(model, "csv")
            val headerLine = result.lines().first()
            assertTrue(headerLine.contains(","), "CSV header should use comma separator")
        }
    }

    @Nested
    @DisplayName("getTableContent JSON export")
    inner class JsonExport {

        @Test
        fun `should return empty JSON array for empty table`() {
            val model = createTableModel(listOf("Col1", "Col2"), emptyList())
            val result = ConsumerEditorUtils.getTableContent(model, "json")
            assertEquals("[]", result.trim())
        }

        @Test
        fun `should produce valid JSON with column names as keys for single row`() {
            val model = createTableModel(listOf("Name", "Age"), listOf(listOf("Alice", "30")))
            val result = ConsumerEditorUtils.getTableContent(model, "json")
            assertTrue(JsonParser.parseString(result).isJsonArray)
            assertTrue(result.contains("\"Name\""))
            assertTrue(result.contains("\"Alice\""))
        }

        @Test
        fun `should produce JSON array with multiple rows`() {
            val model = createTableModel(
                listOf("Id"),
                listOf(listOf("1"), listOf("2"), listOf("3"))
            )
            val result = ConsumerEditorUtils.getTableContent(model, "json")
            // Should contain 3 objects
            assertEquals(3, Regex("\"Id\"").findAll(result).count())
        }

        @Test
        fun `should handle null cell values as JSON null`() {
            val model = createTableModel(listOf("Col"), listOf(listOf(null)))
            val result = ConsumerEditorUtils.getTableContent(model, "json")
            // Gson pretty-prints JsonNull as the literal "null"
            assertEquals(
                """
                [
                  {
                    "Col": null
                  }
                ]
                """.trimIndent(),
                result
            )
        }
    }

    @Nested
    @DisplayName("getTableContent CSV export")
    inner class CsvExport {

        @Test
        fun `should produce CSV header row from column names`() {
            val model = createTableModel(listOf("Name", "Age", "City"), emptyList())
            val result = ConsumerEditorUtils.getTableContent(model, "csv")
            assertEquals("Name,Age,City", result.lines().first())
        }

        @Test
        fun `should produce comma-separated values for each row`() {
            val model = createTableModel(
                listOf("A", "B"),
                listOf(listOf("x", "y"))
            )
            val result = ConsumerEditorUtils.getTableContent(model, "csv")
            val dataLine = result.lines()[1]
            assertEquals("\"x\",\"y\"", dataLine)
        }

        @Test
        fun `should produce tab-separated values when called with TSV extension`() {
            val model = createTableModel(
                listOf("A", "B"),
                listOf(listOf("x", "y"))
            )
            val result = ConsumerEditorUtils.getTableContent(model, "tsv")
            val dataLine = result.lines()[1]
            assertEquals("\"x\"\t\"y\"", dataLine)
        }

        @Test
        fun `should wrap values containing quotes in double-quotes with escaping`() {
            val model = createTableModel(
                listOf("Col"),
                listOf(listOf("say \"hello\""))
            )
            val result = ConsumerEditorUtils.getTableContent(model, "csv")
            val dataLine = result.lines()[1]
            assertEquals("\"say \"\"hello\"\"\"", dataLine)
        }

        @Test
        fun `should replace newlines and tabs in cell values with spaces`() {
            val model = createTableModel(
                listOf("Col"),
                listOf(listOf("line1\nline2\tend"))
            )
            val result = ConsumerEditorUtils.getTableContent(model, "csv")
            val dataLine = result.lines()[1]
            assertFalse(dataLine.contains("\n"))
            assertFalse(dataLine.contains("\t"))
        }

        @Test
        fun `should collapse multiple whitespace characters into single space`() {
            val model = createTableModel(
                listOf("Col"),
                listOf(listOf("a   b"))
            )
            val result = ConsumerEditorUtils.getTableContent(model, "csv")
            val dataLine = result.lines()[1]
            assertEquals("\"a b\"", dataLine)
        }

        @Test
        fun `should handle null cell values as empty quoted string`() {
            val model = createTableModel(
                listOf("Col"),
                listOf(listOf(null))
            )
            val result = ConsumerEditorUtils.getTableContent(model, "csv")
            val dataLine = result.lines()[1]
            assertEquals("\"\"", dataLine)
        }
    }
}
