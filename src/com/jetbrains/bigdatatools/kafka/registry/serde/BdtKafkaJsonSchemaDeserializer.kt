package com.jetbrains.bigdatatools.kafka.registry.serde

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer
import org.apache.kafka.common.errors.SerializationException
import org.everit.json.schema.ValidationException
import java.io.IOException

class BdtKafkaJsonSchemaDeserializer : KafkaJsonSchemaDeserializer<Any?>() {
  private var parsedSchema: ParsedSchema? = null

  override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
    super.configure(configs, isKey)

    parsedSchema = if (isKey)
      configs?.get(FieldType.KEY_PARSED_SCHEMA_CONFIG_KEY) as? ParsedSchema
    else
      configs?.get(FieldType.VALUE_PARSED_SCHEMA_CONFIG_KEY) as? ParsedSchema

  }

  override fun deserialize(topic: String?, bytes: ByteArray?): Any? = if (parsedSchema == null)
    super.deserialize(topic, bytes)
  else
    deserializeWithCustomSchema(bytes)

  private fun deserializeWithCustomSchema(payload: ByteArray?): JsonNode? {
    if (payload == null) {
      return null
    }

    var id = -1
    return try {
      val buffer = getByteBuffer(payload)
      id = buffer.int
      val schema = parsedSchema as JsonSchema
      val length = buffer.limit() - 1 - idSize
      val start = buffer.position() + buffer.arrayOffset()

      var jsonNode: JsonNode? = null
      try {
        jsonNode = objectMapper.readValue(buffer.array(), start, length, JsonNode::class.java)
        schema.validate(jsonNode)
        jsonNode
      }
      catch (e: JsonProcessingException) {
        throw SerializationException("JSON $jsonNode does not match schema ${schema.canonicalString()}", e)
      }
      catch (e: ValidationException) {
        throw SerializationException("JSON $jsonNode does not match schema ${schema.canonicalString()}", e)
      }
    }
    catch (e: IOException) {
      throw SerializationException("Error deserializing JSON message for id $id", e)
    }
    catch (e: RuntimeException) {
      throw SerializationException("Error deserializing JSON message for id $id", e)
    }
    catch (e: RestClientException) {
      throw toKafkaException(e, "Error retrieving JSON schema for id $id")
    }
  }
}