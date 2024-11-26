package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
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

  fun getStartWith(startWithType: ConsumerStartType,
                   startOffsetText: String,
                   startDate: Date?,
                   consumerGroup: String?): ConsumerStartWith {
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
        val parsedCell = JsonPrimitive(cellValue)
        jsonObject.add(columnNames[column], parsedCell)
      }

      jsonArray.add(jsonObject)
    }
    return GsonBuilder().setPrettyPrinting().create().toJson(jsonArray)
  }

  private fun <T> getJTableAsCsv(tableModel: ListTableModel<T>, separator: String): String {
    val columnNames = tableModel.columnModel.columns.toList().map { it.headerValue.toString() }

    val builder = StringBuilder()
    builder.append(columnNames.joinToString(separator))
    builder.appendLine()

    for (row in 0 until tableModel.rowCount) {
      for (column in 0 until tableModel.columnCount) {
        builder.append(escapedCellValue(tableModel, row, column))
        if (column < tableModel.columnCount - 1) {
          builder.append(separator)
        }
      }
      builder.appendLine()
    }

    return builder.toString()
  }

  private fun <T> escapedCellValue(tableModel: ListTableModel<T>, row: Int, column: Int): String {
    val cellValue = tableModel.getValueAt(row, column)?.toString()
      ?.replace(LINE_SEPARATOR, " ")
      ?.replace(TAB_CHAR, " ")
      ?.replace(REPLACE_SPACES_PATTERN, " ")

    if (cellValue == null)
      return "\"\""

    return StringUtil.escapeChar(cellValue, '"')
  }
}

private const val LINE_SEPARATOR = "\n"
private const val TAB_CHAR = "\t"

private val REPLACE_SPACES_PATTERN = Regex("\\s+")