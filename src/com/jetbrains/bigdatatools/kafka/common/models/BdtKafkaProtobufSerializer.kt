package com.jetbrains.bigdatatools.kafka.common.models

import com.google.protobuf.Message
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer

class BdtKafkaProtobufSerializer(client: SchemaRegistryClient?, schemaName: String) : KafkaProtobufSerializer<Message?>(client) {
  init {
    keySubjectNameStrategy = CustomSubjectStrategy(schemaName)
    valueSubjectNameStrategy = CustomSubjectStrategy(schemaName)
  }
}