package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
import java.util.*

object ConsumerEditorUtils {
  fun parsePartitionsText(partitionText: String): List<Int> {
    val partitionsStrings = partitionText.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val partitions = partitionsStrings.flatMap { p ->
      if (!p.contains("-"))
        listOfNotNull(p.toIntOrNull())
      else {
        val range = p.split("-").map { it.trim() }
        val start = range.first().trim().toIntOrNull() ?: return@flatMap emptyList<Int>()
        val end = range.last().trim().toIntOrNull() ?: return@flatMap emptyList<Int>()
        start..end
      }
    }
    return partitions
  }

  fun getStartWith(startWithType: ConsumerStartType,
                   startOffsetText: String,
                   startDate: Date?): ConsumerStartWith {
    val startOffset: Long? = when (startWithType) {
      ConsumerStartType.OFFSET -> startOffsetText.ifBlank { null }?.toLongOrNull()
      ConsumerStartType.LATEST_OFFSET_MINUS_X -> startOffsetText.ifBlank { null }?.toLongOrNull()?.times(-1)
      ConsumerStartType.THE_BEGINNING -> 0
      else -> startOffsetText.ifBlank { null }?.toLongOrNull()
    }

    val calendar = Calendar.getInstance()
    calendar.time = Date()

    val startTime = when (startWithType) {
      ConsumerStartType.NOW -> null
      ConsumerStartType.LAST_HOUR -> {
        calendar.add(Calendar.HOUR_OF_DAY, -1)
        calendar.time
      }
      ConsumerStartType.TODAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.time
      }
      ConsumerStartType.YESTERDAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.time
      }

      ConsumerStartType.SPECIFIC_DATE -> {

        startDate
      }
      else -> null
    }
    return ConsumerStartWith(offset = startOffset, time = startTime?.time)
  }
}