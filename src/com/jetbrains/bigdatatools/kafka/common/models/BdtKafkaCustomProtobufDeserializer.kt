package io.confluent.kafka.common.models

import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import io.confluent.kafka.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import org.apache.kafka.common.serialization.Deserializer

class BdtKafkaCustomProtobufDeserializer(private val producerConfig: ConsumerProducerFieldConfig) : Deserializer<Message> {
  override fun deserialize(topic: String?, data: ByteArray?): Message? {
    if (data == null || data.isEmpty())
      return null

    val schema = producerConfig.parsedSchema as ProtobufSchema
    val descriptor = schema.toDescriptor()
                     ?: throw org.apache.kafka.common.errors.SerializationException("Could not find descriptor with name " + schema.name())
    return DynamicMessage.parseFrom(descriptor, data.inputStream())
  }
}