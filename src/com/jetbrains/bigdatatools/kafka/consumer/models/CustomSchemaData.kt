package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.models.KafkaCustomSchemaSource

data class CustomSchemaData(
  var customFile: String? = null,
  var customSchemaSource: KafkaCustomSchemaSource? = null,
  var customSchemaImplicit: String? = null,
)