package io.confluent.intellijplugin.consumer.editor

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.util.text.StringUtil
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.consumer.models.ConsumerStartWith
import java.util.*

internal object ConsumerEditorUtils {
    fun parsePartitionsText(partitionText: String?): List<Int> {
        partitionText ?: return emptyList()
        val partitionsStrings = partitionText.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return partitionsStrings.flatMap { p ->
            if (!p.contains("-"))
                listOfNotNull(p.toIntOrNull())
            else {
                val range = p.split("-").map { it.trim() }
                val start = range.first().trim().toIntOrNull() ?: return@flatMap emptyList()
                val end = range.last().trim().toIntOrNull() ?: return@flatMap emptyList()
                start..end
            }
        }
    }

    fun getStartWith(
        startWithType: ConsumerStartType,
        startOffsetText: String,
        startDate: Date?,
        consumerGroup: String?
    ): ConsumerStartWith {
        val startOffset: Long? = when (startWithType) {
            ConsumerStartType.OFFSET -> startOffsetText.ifBlank { null }?.toLongOrNull()
            ConsumerStartType.LATEST_OFFSET_MINUS_X -> startOffsetText.ifBlank { null }?.toLongOrNull()?.times(-1)
            ConsumerStartType.THE_BEGINNING -> 0
            else -> null
        }

        val startTime = if (startWithType == ConsumerStartType.SPECIFIC_DATE)
            startDate
        else
            null

        @Suppress("HardCodedStringLiteral") // consumerGroup - user defined name and cannot be localized.
        return ConsumerStartWith(startWithType, time = startTime?.time, offset = startOffset, consumerGroup)
    }

    fun <T> getTableContent(tableModel: ListTableModel<T>, fileExtension: String): String = when (fileExtension) {
        "json" -> getJTableAsJson(tableModel)
        "tsv" -> getJTableAsCsv(tableModel, "\t")
        else -> getJTableAsCsv(tableModel, ",")
    }

    private fun <T> getJTableAsJson(tableModel: ListTableModel<T>): String {
        val columnNames = tableModel.columnModel.columns.toList().map { it.headerValue.toString() }

        val jsonArray = JsonArray()
        for (row in 0 until tableModel.rowCount) {
            val jsonObject = JsonObject()

            for (column in 0 until tableModel.columnCount) {
                val cellValue = tableModel.getValueAt(row, column)?.toString()
                if (cellValue == null) {
                    jsonObject.add(columnNames[column], com.google.gson.JsonNull.INSTANCE)
                } else {
                    jsonObject.add(columnNames[column], JsonPrimitive(cellValue))
                }
            }

            jsonArray.add(jsonObject)
        }
        return GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(jsonArray)
    }

    private fun <T> getJTableAsCsv(tableModel: ListTableModel<T>, separator: String): String {
        val columnNames = tableModel.columnModel.columns.toList().map { it.headerValue.toString() }

        val builder = StringBuilder()
        builder.append(columnNames.joinToString(separator))
        builder.appendLine()

        for (row in 0 until tableModel.rowCount) {
            for (column in 0 until tableModel.columnCount) {
                builder.append(exportEntryAsCsv(tableModel, row, column))
                if (column < tableModel.columnCount - 1) {
                    builder.append(separator)
                }
            }
            builder.appendLine()
        }

        return builder.toString()
    }

    private fun <T> exportEntryAsCsv(tableModel: ListTableModel<T>, row: Int, column: Int): String {
        val cellValue = tableModel.getValueAt(row, column)?.toString()
            ?.replace(LINE_SEPARATOR, " ")
            ?.replace(TAB_CHAR, " ")
            ?.replace(REPLACE_SPACES_PATTERN, " ")

        if (cellValue == null)
            return "\"\""

        return StringUtil.wrapWithDoubleQuote(StringUtil.replace(cellValue, "\"", "\"\""))
    }
}

private const val LINE_SEPARATOR = "\n"
private const val TAB_CHAR = "\t"

private val REPLACE_SPACES_PATTERN = Regex("\\s+")