package io.confluent.intellijplugin.common.models

import com.fasterxml.jackson.databind.JsonNode
import io.confluent.intellijplugin.testutil.fixture
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BdtKafkaJsonSchemaSerializerTest {

    private val subject = "users-value"
    private val schema = JsonSchema(fixture("schemas", "user-json-schema.json"))
    private val client = MockSchemaRegistryClient(listOf(JsonSchemaProvider())).apply { register(subject, schema) }

    // The producer passes a {schema, payload} envelope built by JsonSchemaUtils.toObject. SR 8.2+ only unwraps it
    // when envelopeDetection is set, which happens in configure() — never called for our serializer.
    @Test
    fun `should serialize a schema envelope using the registered schema`() {
        val record = JsonSchemaUtils.toObject("""{"name":"alice","age":30}""", schema)

        val bytes = BdtKafkaJsonSchemaSerializer(client, subject).serialize("users", record)
        val decoded = KafkaJsonSchemaDeserializer<JsonNode>(client).deserialize("users", bytes)

        assertEquals("alice", decoded["name"].asText())
        assertEquals(30, decoded["age"].asInt())
    }
}
