package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.google.gson.*
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
import java.util.*
import javax.swing.JTable

object ConsumerEditorUtils {
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

  fun getTableContent(table: JTable, fileExtension: String): String = when (fileExtension) {
    "json" -> getJTableAsJson(table)
    "tsv" -> getJTableAsCsv(table, "\t")
    else -> getJTableAsCsv(table, ",")
  }

  private fun getJTableAsJson(table: JTable): String {
    val columnNames = table.columnModel.columns.toList().map { it.headerValue.toString() }

    val jsonArray = JsonArray()
    for (row in 0 until table.rowCount) {
      val jsonObject = JsonObject()

      for (column in 0 until table.columnCount) {
        val cellValue = table.getValueAt(row, column)?.toString()

        val parsedCell = try {
          JsonParser.parseString(cellValue)
        }
        catch (_: JsonSyntaxException) {
          JsonPrimitive(cellValue)
        }

        jsonObject.add(columnNames[column], parsedCell)
      }

      jsonArray.add(jsonObject)
    }
    return GsonBuilder().setPrettyPrinting().create().toJson(jsonArray)
  }

  private fun getJTableAsCsv(table: JTable, separator: String): String {
    val columnNames = table.columnModel.columns.toList().map { it.headerValue.toString() }

    val builder = StringBuilder()
    builder.append(columnNames.joinToString(separator))
    builder.appendLine()

    for (row in 0 until table.rowCount) {
      for (column in 0 until table.columnCount) {
        builder.append(getCellValue(table, row, column))
        if (column < table.columnCount - 1) {
          builder.append(separator)
        }
      }
      builder.appendLine()
    }

    return builder.toString()
  }

  private fun getCellValue(table: JTable, row: Int, column: Int): String {
    val cellValue = table.getValueAt(row, column)?.toString()
      ?.replace(LINE_SEPARATOR, " ")
      ?.replace(TAB_CHAR, " ")
      ?.replace("\\s+".toRegex(), " ")

    if (cellValue == null)
      return "\"\""

    val stringValue = cellValue.toString()
    return "\"${stringValue.replace("\"", "\"\"")}\""
  }
}

private const val LINE_SEPARATOR = "\n"
private const val TAB_CHAR = "\t"
