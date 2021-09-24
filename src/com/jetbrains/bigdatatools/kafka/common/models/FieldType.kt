package com.jetbrains.bigdatatools.kafka.common.models

enum class FieldType(val value: String) {
  JSON("JSON"),
  STRING("String"),
  LONG("Long"),
  DOUBLE("Double"),
  FLOAT("Float"),
  BASE64("Bytes (base64)"),
  NULL("null")
}