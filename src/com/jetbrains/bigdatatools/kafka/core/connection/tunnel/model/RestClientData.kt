package io.confluent.kafka.core.connection.tunnel.model

import io.confluent.kafka.core.settings.DoNotSerialize

interface RestClientData {
  var operationTimeout: String?
  @DoNotSerialize
  var headers: Map<String, String>?
}
