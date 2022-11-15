package com.jetbrains.bigdatatools.kafka.registry.serde

import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.common.errors.SerializationException
import java.io.ByteArrayInputStream
import java.io.IOException

class BdtKafkaProtobufDeserializer : KafkaProtobufDeserializer<Message>() {
  private var parsedSchema: ParsedSchema? = null

  override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
    super.configure(configs, isKey)

    parsedSchema = if (isKey)
      configs?.get(FieldType.KEY_PARSED_SCHEMA_CONFIG_KEY) as? ParsedSchema
    else
      configs?.get(FieldType.VALUE_PARSED_SCHEMA_CONFIG_KEY) as? ParsedSchema

  }

  override fun deserialize(topic: String?, bytes: ByteArray?): Message? = if (parsedSchema == null)
    super.deserialize(topic, bytes)
  else
    deserializeWithCustomSchema(bytes)

  private fun deserializeWithCustomSchema(payload: ByteArray?): Message? {
    if (payload == null) {
      return null
    }
    var id = -1
    return try {
      val buffer = getByteBuffer(payload)
      id = buffer.int
      val schema = parsedSchema as ProtobufSchema

      val length = buffer.limit() - 1 - idSize
      val start = buffer.position() + buffer.arrayOffset()

      val value = if (parseMethod != null) {
        try {
          parseMethod.invoke(null, buffer)
        }
        catch (e: java.lang.Exception) {
          throw ConfigException("Not a valid protobuf builder", e)
        }
      }
      else {
        val descriptor = schema.toDescriptor()
                         ?: throw SerializationException("Could not find descriptor with name " + schema.name())
        DynamicMessage.parseFrom(descriptor,
                                 ByteArrayInputStream(buffer.array(), start, length)
        )
      }
      value as? Message
    }
    catch (e: IOException) {
      throw SerializationException("Error deserializing Protobuf message for id $id", e)
    }
    catch (e: RuntimeException) {
      throw SerializationException("Error deserializing Protobuf message for id $id", e)
    }
    catch (e: RestClientException) {
      throw toKafkaException(e, "Error retrieving Protobuf schema for id $id")
    }
  }
}