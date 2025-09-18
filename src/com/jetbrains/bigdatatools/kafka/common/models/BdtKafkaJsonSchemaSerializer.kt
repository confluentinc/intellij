package com.jetbrains.bigdatatools.kafka.common.models

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer

class BdtKafkaJsonSchemaSerializer(client: SchemaRegistryClient?, schemaName: String) : KafkaJsonSchemaSerializer<Any>(client) {
  init {
    keySubjectNameStrategy = CustomSubjectStrategy(schemaName)
    valueSubjectNameStrategy = CustomSubjectStrategy(schemaName)
  }
}