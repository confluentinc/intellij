package io.confluent.intellijplugin.common.models

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer

class BdtKafkaJsonSchemaSerializer(client: SchemaRegistryClient?, schemaName: String) :
    KafkaJsonSchemaSerializer<Any>(client) {
    init {
        keySubjectNameStrategy = CustomSubjectStrategy(schemaName)
        valueSubjectNameStrategy = CustomSubjectStrategy(schemaName)
        // SR 8.2+ only unwraps {schema, payload} envelopes when this is set, and it is set in configure(), which
        // we never call. Without it the serializer generates a schema from the record's class instead (#637).
        envelopeDetection = true
    }
}