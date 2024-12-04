package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.models.KafkaCustomSchemaSource

data class CustomSchemaData(
  val customFile: String? = null,
  val customSchemaSource: KafkaCustomSchemaSource? = null,
  val customSchemaImplicit: String? = null,
)