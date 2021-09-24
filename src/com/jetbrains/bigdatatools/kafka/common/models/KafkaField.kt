package com.jetbrains.bigdatatools.kafka.common.models

import com.intellij.util.Base64

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