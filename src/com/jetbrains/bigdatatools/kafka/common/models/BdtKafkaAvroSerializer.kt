package com.jetbrains.bigdatatools.kafka.common.models

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer

class BdtKafkaAvroSerializer(client: SchemaRegistryClient?, schemaName: String) : KafkaAvroSerializer(client) {
  init {
    keySubjectNameStrategy = CustomSubjectStrategy(schemaName)
    valueSubjectNameStrategy = CustomSubjectStrategy(schemaName)
  }
}