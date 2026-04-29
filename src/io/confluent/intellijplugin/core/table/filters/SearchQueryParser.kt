package io.confluent.intellijplugin.core.table.filters

/**
 * Parses and builds search query text with column-specific filter syntax.
 *
 * Syntax:
 * - `columnName:value` — filter a specific column
 * - `columnName:"value with spaces"` — quoted value for spaces
 * - `free text` — matches across all columns
 *
 * @param columnNameToIndex maps lowercase column names to model indices
 */
class SearchQueryParser(private val columnNameToIndex: Map<String, Int>) {

    data class ParsedSearch(
        val columnFilters: Map<Int, String>,
        val freeText: String
    )

    fun parse(text: String): ParsedSearch {
        val columnFilters = mutableMapOf<Int, String>()
        val freeTextParts = mutableListOf<String>()
        var pos = 0

        while (pos < text.length) {
            if (text[pos] == ' ') { pos++; continue }

            val colonIndex = text.indexOf(':', pos)
            if (colonIndex > pos && !text.substring(pos, colonIndex).contains(' ')) {
                val colName = text.substring(pos, colonIndex).lowercase()
                val modelIndex = columnNameToIndex[colName]
                if (modelIndex != null) {
                    pos = colonIndex + 1
                    val (value, nextPos) = extractValue(text, pos)
                    columnFilters[modelIndex] = value
                    pos = nextPos
                    continue
                }
            }

            val nextSpace = text.indexOf(' ', pos)
            val end = if (nextSpace == -1) text.length else nextSpace
            freeTextParts.add(text.substring(pos, end))
            pos = end
        }

        return ParsedSearch(columnFilters, freeTextParts.joinToString(" "))
    }

    fun buildSearchText(columnFilters: Map<Int, String>, freeText: String): String {
        val indexToName = columnNameToIndex.entries.associate { (name, index) -> index to name }
        val parts = columnFilters.entries
            .sortedBy { it.key }
            .mapNotNull { (modelIndex, value) ->
                val colName = indexToName[modelIndex] ?: return@mapNotNull null
                if (value.contains(' ')) "$colName:\"$value\"" else "$colName:$value"
            }
            .toMutableList()

        if (freeText.isNotBlank()) {
            parts.add(freeText)
        }
        return parts.joinToString(" ")
    }

    private fun extractValue(text: String, pos: Int): Pair<String, Int> {
        if (pos < text.length && text[pos] == '"') {
            val closeQuote = text.indexOf('"', pos + 1)
            return if (closeQuote == -1) {
                text.substring(pos + 1) to text.length
            } else {
                text.substring(pos + 1, closeQuote) to closeQuote + 1
            }
        }
        val nextSpace = text.indexOf(' ', pos)
        val end = if (nextSpace == -1) text.length else nextSpace
        return text.substring(pos, end) to end
    }
}
