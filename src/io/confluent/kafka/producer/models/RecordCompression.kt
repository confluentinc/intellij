package io.confluent.kafka.producer.models

enum class RecordCompression {
  NONE, GZIP, SNAPPY, LZ4, ZSTD
}