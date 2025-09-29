package io.confluent.intellijplugin.consumer.models

import io.confluent.intellijplugin.common.models.KafkaCustomSchemaSource

data class CustomSchemaData(
  var customFile: String? = null,
  var customSchemaSource: KafkaCustomSchemaSource? = null,
  var customSchemaImplicit: String? = null,
)