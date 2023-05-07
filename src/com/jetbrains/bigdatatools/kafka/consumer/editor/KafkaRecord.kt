package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import java.nio.charset.StandardCharsets

data class KafkaRecord(val keyType: FieldType, val valueType: FieldType,
                       val error: Throwable?,
                       val key: Any?, val value: Any?,
                       val topic: String,
                       val partition: Int,
                       val offset: Long,
                       val timestamp: Long,
                       val timestampType: TimestampType,
                       val keySize: Int, val valueSize: Int,
                       val headers: List<Property>) {
  val keyText = if (error == null) KafkaEditorUtils.getValueAsString(keyType, key) else null
  val valueText = if (error == null) KafkaEditorUtils.getValueAsString(valueType, value) else null
  val errorText = error?.message ?: error?.let { it::class.java.simpleName } ?: "<Unknown>"

  companion object {
    fun createFor(keyType: FieldType, valueType: FieldType, record: Result<ConsumerRecord<Any, Any>>) =
      if (record.isSuccess) {
        val rec = record.getOrNull()!!
        KafkaRecord(
          keyType = keyType,
          valueType = valueType,
          key = rec.key(),
          value = rec.value(),
          topic = rec.topic(),
          partition = rec.partition(),
          offset = rec.offset(),
          timestamp = rec.timestamp(),
          keySize = rec.serializedKeySize(),
          valueSize = rec.serializedValueSize(),
          timestampType = rec.timestampType(),
          headers = rec.headers().toList().map { Property(name = it.key(), value = String(it.value(), StandardCharsets.UTF_8)) },
          error = null)
      }
      else {
        KafkaRecord(
          keyType = keyType,
          valueType = valueType,
          key = null,
          value = null,
          partition = -1,
          offset = -1,
          topic = "",
          timestamp = System.currentTimeMillis(),
          keySize = 0,
          valueSize = 0,
          timestampType = TimestampType.NO_TIMESTAMP_TYPE,
          headers = emptyList(),
          error = record.exceptionOrNull())
      }
  }
}