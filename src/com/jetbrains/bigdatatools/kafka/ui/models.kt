package com.jetbrains.bigdatatools.kafka.ui

import com.intellij.util.Base64

enum class FieldType(val value: String) {
  JSON("JSON"),
  STRING("String"),
  LONG("Long"),
  DOUBLE("Double"),
  FLOAT("Float"),
  BASE64("Bytes (base64)"),
  NULL("null")
}

data class KafkaField(val type: FieldType, val text: String?) {
  val value = when (type) {
    FieldType.JSON -> text
    FieldType.STRING -> text
    FieldType.LONG -> text?.toLong()
    FieldType.DOUBLE -> text?.toDouble()
    FieldType.FLOAT -> text?.toFloat()
    FieldType.BASE64 -> text?.let { Base64.decode(it) }
    FieldType.NULL -> null
  }
}

enum class RecordCompression {
  NONE, GZIP, SNAPPY, LZ4, ZSTD
}

enum class AcksType(val value: Int) {
  NONE(0), LEADER(1), ALL(-1)
}

enum class KafkaEditorType {
  CONSUMER, PRODUCER
}