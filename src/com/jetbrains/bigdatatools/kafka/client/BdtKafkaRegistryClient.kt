package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient

class BdtKafkaRegistryClient(url: String) : CachedSchemaRegistryClient(listOf(url),
                                                                       100,
                                                                       KafkaRegistryUtil.registrySchemaProviders,
                                                                       null), Disposable {
  override fun dispose() = Unit
}