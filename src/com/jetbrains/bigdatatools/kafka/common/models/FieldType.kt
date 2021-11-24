package com.jetbrains.bigdatatools.kafka.common.models

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.Nls

enum class FieldType(@Nls val title: String) {
  JSON(KafkaMessagesBundle.message("field.type.json")),
  STRING(KafkaMessagesBundle.message("field.type.string")),
  LONG(KafkaMessagesBundle.message("field.type.long")),
  DOUBLE(KafkaMessagesBundle.message("field.type.double")),
  FLOAT(KafkaMessagesBundle.message("field.type.float")),
  BASE64(KafkaMessagesBundle.message("field.type.base64")),
  NULL(KafkaMessagesBundle.message("field.type.null"));

  fun getDeserializationClass() = when (this) {
    STRING, JSON -> StringDeserializer()
    LONG -> LongDeserializer()
    DOUBLE -> DoubleDeserializer()
    FLOAT -> FloatDeserializer()
    BASE64 -> BytesDeserializer()
    NULL -> VoidDeserializer()
  }

  fun getSerializer() = when (this) {
    STRING -> StringSerializer()
    JSON -> StringSerializer()
    LONG -> LongSerializer()
    DOUBLE -> DoubleSerializer()
    FLOAT -> FloatSerializer()
    BASE64 -> BytesSerializer()
    NULL -> VoidSerializer()
  }
}