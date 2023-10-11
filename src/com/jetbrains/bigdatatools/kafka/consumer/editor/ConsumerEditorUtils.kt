package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
import java.util.*

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
    return ConsumerStartWith(startWithType, time = startTime?.time, offset = startOffset, consumerGroup)
  }
}