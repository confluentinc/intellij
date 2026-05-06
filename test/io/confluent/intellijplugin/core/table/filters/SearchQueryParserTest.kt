package io.confluent.intellijplugin.core.table.filters

import io.confluent.intellijplugin.core.table.filters.SearchQueryParser.ParsedSearch
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SearchQueryParserTest {

    private val columnMap = mapOf(
        "key" to 0,
        "value" to 1,
        "partition" to 2,
        "offset" to 3,
        "timestamp" to 4
    )

    private val parser = SearchQueryParser(columnMap)

    @Nested
    inner class Parse {

        @Test
        fun `should return empty result for empty input`() {
            val result = parser.parse("")
            assertEquals(emptyMap<Int, String>(), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should return empty result for blank input`() {
            val result = parser.parse("   ")
            assertEquals(emptyMap<Int, String>(), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should parse simple free text`() {
            val result = parser.parse("hello world")
            assertEquals(emptyMap<Int, String>(), result.columnFilters)
            assertEquals("hello world", result.freeText)
        }

        @Test
        fun `should parse single column filter`() {
            val result = parser.parse("key:hello")
            assertEquals(mapOf(0 to "hello"), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should parse multiple column filters`() {
            val result = parser.parse("key:hello value:world")
            assertEquals(mapOf(0 to "hello", 1 to "world"), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should parse quoted value with spaces`() {
            val result = parser.parse("key:\"hello world\"")
            assertEquals(mapOf(0 to "hello world"), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should handle unclosed quote gracefully`() {
            val result = parser.parse("key:\"hello world")
            assertEquals(mapOf(0 to "hello world"), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should parse mixed column filters and free text`() {
            val result = parser.parse("key:hello some free text value:world")
            assertEquals(mapOf(0 to "hello", 1 to "world"), result.columnFilters)
            assertEquals("some free text", result.freeText)
        }

        @Test
        fun `should treat unknown column name as free text`() {
            val result = parser.parse("bogus:value")
            assertEquals(emptyMap<Int, String>(), result.columnFilters)
            assertEquals("bogus:value", result.freeText)
        }

        @Test
        fun `should match column names case-insensitively`() {
            val result = parser.parse("Key:hello VALUE:world")
            assertEquals(mapOf(0 to "hello", 1 to "world"), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should handle empty value after colon`() {
            val result = parser.parse("key: other")
            assertEquals(mapOf(0 to ""), result.columnFilters)
            assertEquals("other", result.freeText)
        }

        @Test
        fun `should use last value when column appears multiple times`() {
            val result = parser.parse("key:first key:second")
            assertEquals(mapOf(0 to "second"), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should handle colon in free text when no column match`() {
            val result = parser.parse("not-a-column:whatever")
            assertEquals(emptyMap<Int, String>(), result.columnFilters)
            assertEquals("not-a-column:whatever", result.freeText)
        }

        @Test
        fun `should handle multiple spaces between tokens`() {
            val result = parser.parse("key:hello   value:world")
            assertEquals(mapOf(0 to "hello", 1 to "world"), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should handle free text before and after column filters`() {
            val result = parser.parse("before key:hello after")
            assertEquals(mapOf(0 to "hello"), result.columnFilters)
            assertEquals("before after", result.freeText)
        }

        @Test
        fun `should handle all columns`() {
            val result = parser.parse("key:k value:v partition:0 offset:100 timestamp:today")
            assertEquals(
                mapOf(0 to "k", 1 to "v", 2 to "0", 3 to "100", 4 to "today"),
                result.columnFilters
            )
            assertEquals("", result.freeText)
        }

        @Test
        fun `should handle quoted empty value`() {
            val result = parser.parse("key:\"\"")
            assertEquals(mapOf(0 to ""), result.columnFilters)
            assertEquals("", result.freeText)
        }

        @Test
        fun `should handle text immediately after quoted value`() {
            val result = parser.parse("key:\"hello\" freetext")
            assertEquals(mapOf(0 to "hello"), result.columnFilters)
            assertEquals("freetext", result.freeText)
        }
    }

    @Nested
    inner class BuildSearchText {

        @Test
        fun `should return empty string for no filters and no free text`() {
            val result = parser.buildSearchText(emptyMap(), "")
            assertEquals("", result)
        }

        @Test
        fun `should build column filters only`() {
            val result = parser.buildSearchText(mapOf(0 to "hello", 1 to "world"), "")
            assertEquals("key:hello value:world", result)
        }

        @Test
        fun `should build free text only`() {
            val result = parser.buildSearchText(emptyMap(), "some text")
            assertEquals("some text", result)
        }

        @Test
        fun `should quote values containing spaces`() {
            val result = parser.buildSearchText(mapOf(0 to "hello world"), "")
            assertEquals("key:\"hello world\"", result)
        }

        @Test
        fun `should combine column filters and free text`() {
            val result = parser.buildSearchText(mapOf(0 to "hello"), "free text")
            assertEquals("key:hello free text", result)
        }

        @Test
        fun `should order column filters by model index`() {
            val result = parser.buildSearchText(mapOf(3 to "100", 0 to "hello", 1 to "world"), "")
            assertEquals("key:hello value:world offset:100", result)
        }

        @Test
        fun `should skip columns with no name mapping`() {
            val result = parser.buildSearchText(mapOf(0 to "hello", 99 to "unmapped"), "")
            assertEquals("key:hello", result)
        }

        @Test
        fun `should not quote values without spaces`() {
            val result = parser.buildSearchText(mapOf(0 to "nospaces"), "")
            assertEquals("key:nospaces", result)
        }
    }

    @Nested
    inner class RoundTrip {

        @Test
        fun `should round-trip simple column filter`() {
            val original = "key:hello value:world"
            val parsed = parser.parse(original)
            val rebuilt = parser.buildSearchText(parsed.columnFilters, parsed.freeText)
            assertEquals(original, rebuilt)
        }

        @Test
        fun `should round-trip quoted value`() {
            val original = "key:\"hello world\""
            val parsed = parser.parse(original)
            val rebuilt = parser.buildSearchText(parsed.columnFilters, parsed.freeText)
            assertEquals(original, rebuilt)
        }

        @Test
        fun `should round-trip mixed filters and free text`() {
            val original = "key:hello free text"
            val parsed = parser.parse(original)
            val rebuilt = parser.buildSearchText(parsed.columnFilters, parsed.freeText)
            assertEquals(original, rebuilt)
        }

        @Test
        fun `should round-trip free text only`() {
            val original = "just some free text"
            val parsed = parser.parse(original)
            val rebuilt = parser.buildSearchText(parsed.columnFilters, parsed.freeText)
            assertEquals(original, rebuilt)
        }

        @Test
        fun `should normalize multiple spaces on round-trip`() {
            val original = "key:hello    value:world"
            val parsed = parser.parse(original)
            val rebuilt = parser.buildSearchText(parsed.columnFilters, parsed.freeText)
            assertEquals("key:hello value:world", rebuilt)
        }
    }
}
