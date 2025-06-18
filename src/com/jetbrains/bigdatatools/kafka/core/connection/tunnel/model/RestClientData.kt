package com.jetbrains.bigdatatools.kafka.core.connection.tunnel.model

import com.jetbrains.bigdatatools.kafka.core.settings.DoNotSerialize

interface RestClientData {
  var operationTimeout: String?
  @DoNotSerialize
  var headers: Map<String, String>?
}
