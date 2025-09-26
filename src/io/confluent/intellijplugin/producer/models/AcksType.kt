package io.confluent.intellijplugin.producer.models

enum class AcksType(val value: Int) {
  NONE(0), LEADER(1), ALL(-1)
}