package com.jetbrains.bigdatatools.kafka.common.models

import org.apache.kafka.common.serialization.*

enum class FieldType(val value: String) {
  JSON("JSON"),
  STRING("String"),
  LONG("Long"),
  DOUBLE("Double"),
  FLOAT("Float"),
  BASE64("Bytes (base64)"),
  NULL("null");

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