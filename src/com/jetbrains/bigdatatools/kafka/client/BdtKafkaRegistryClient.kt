package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.RestService

class BdtKafkaRegistryClient(restService: RestService) : CachedSchemaRegistryClient(restService,
                                                                                    100,
                                                                                    KafkaRegistryUtil.registrySchemaProviders,
                                                                                    null, null), Disposable {
  override fun dispose() = Unit
}