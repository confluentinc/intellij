package io.confluent.kafka.consumer.models

import io.confluent.kafka.common.models.KafkaCustomSchemaSource

data class CustomSchemaData(
  var customFile: String? = null,
  var customSchemaSource: KafkaCustomSchemaSource? = null,
  var customSchemaImplicit: String? = null,
)