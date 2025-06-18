package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.core.settings.connections.Property
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.nio.charset.StandardCharsets

data class KafkaRecord(val keyType: KafkaFieldType,
                       val valueType: KafkaFieldType,
                       val error: Throwable?,
                       val key: Any?,
                       val value: Any?,
                       val topic: String,
                       val partition: Int,
                       val offset: Long,
                       val duration: Long,
                       val timestamp: Long,
                       val keySize: Int,
                       val valueSize: Int,
                       val headers: List<Property>,
                       val keyFormat: KafkaRegistryFormat,
                       val valueFormat: KafkaRegistryFormat,
                       val errror: Throwable?) {
  val keyText = if (error == null) KafkaEditorUtils.getValueAsString(keyType, key, keyFormat) else null
  val valueText = if (error == null) KafkaEditorUtils.getValueAsString(valueType, value, valueFormat) else null
  val errorText = error?.message ?: error?.let { it::class.java.simpleName } ?: "<Unknown>"

  companion object {
    fun createFor(keyType: KafkaFieldType?, valueType: KafkaFieldType?,
                  keyFormat: KafkaRegistryFormat?, valueFormat: KafkaRegistryFormat?,
                  record: Result<ConsumerRecord<Any, Any>>,
                  errorPartition: Int? = null,
                  errorOffset: Long? = null) =
      if (record.isSuccess) {
        val rec = record.getOrNull()!!
        KafkaRecord(
          keyType = keyType ?: KafkaFieldType.STRING,
          valueType = valueType ?: KafkaFieldType.STRING,
          error = null,
          key = rec.key(),
          value = rec.value(),
          topic = rec.topic(),
          partition = rec.partition(),
          offset = rec.offset(),
          duration = -1,
          timestamp = rec.timestamp(),
          keySize = rec.serializedKeySize(),
          valueSize = rec.serializedValueSize(),
          headers = rec.headers()?.toList()?.map {
            Property(name = it.key() ?: "", value = String(it.value() ?: byteArrayOf(0), StandardCharsets.UTF_8))
          } ?: emptyList(),
          keyFormat = keyFormat ?: KafkaRegistryFormat.UNKNOWN,
          valueFormat = valueFormat ?: KafkaRegistryFormat.UNKNOWN,
          errror = record.exceptionOrNull())
      }
      else {
        KafkaRecord(
          errror = record.exceptionOrNull(),
          keyType = keyType ?: KafkaFieldType.STRING,
          valueType = valueType ?: KafkaFieldType.STRING,
          error = record.exceptionOrNull(),
          key = null,
          value = null,
          topic = "",
          partition = errorPartition ?: -1,
          offset = errorOffset ?: -1,
          duration = -1,
          timestamp = System.currentTimeMillis(),
          keySize = 0,
          valueSize = 0,
          headers = emptyList(),
          keyFormat = keyFormat ?: KafkaRegistryFormat.UNKNOWN,
          valueFormat = keyFormat ?: KafkaRegistryFormat.UNKNOWN)
      }

    fun createFor(keyConfig: ConsumerProducerFieldConfig,
                  valueConfig: ConsumerProducerFieldConfig,
                  metadata: RecordMetadata?,
                  duration: Long,
                  headers: List<Property>) = KafkaRecord(
      keyType = keyConfig.type,
      valueType = valueConfig.type,
      error = null,
      key = keyConfig.getValueObj(),
      value = valueConfig.getValueObj(),
      topic = metadata?.topic() ?: "",
      partition = metadata?.partition() ?: -1,
      offset = metadata?.offset() ?: -1,
      duration = duration,
      timestamp = metadata?.timestamp() ?: -1,
      keySize = metadata?.serializedKeySize() ?: 0,
      valueSize = metadata?.serializedValueSize() ?: 0,
      headers = headers.toList(),
      keyFormat = keyConfig.schemaFormat,
      valueFormat = valueConfig.schemaFormat,
      errror = null)
  }
}