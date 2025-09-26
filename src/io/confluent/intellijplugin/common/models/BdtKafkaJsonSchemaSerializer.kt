package io.confluent.intellijplugin.common.models

import io.confluent.intellijplugin.schemaregistry.client.SchemaRegistryClient
import io.confluent.intellijplugin.serializers.json.KafkaJsonSchemaSerializer

class BdtKafkaJsonSchemaSerializer(client: SchemaRegistryClient?, schemaName: String) : KafkaJsonSchemaSerializer<Any>(client) {
  init {
    keySubjectNameStrategy = CustomSubjectStrategy(schemaName)
    valueSubjectNameStrategy = CustomSubjectStrategy(schemaName)
  }
}