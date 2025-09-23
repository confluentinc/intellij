package io.confluent.kafka.common.models

import io.confluent.kafka.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.serialization.Deserializer

class BdtKafkaCustomAvroDeserializer(private val producerConfig: ConsumerProducerFieldConfig) : AbstractKafkaAvroDeserializer(), Deserializer<Any> {
  override fun deserialize(topic: String?, data: ByteArray?): Any? {
    if (data == null || data.isEmpty())
      return null

    val rawSchema = (producerConfig.parsedSchema as AvroSchema).rawSchema()
    val decoderFactory = DecoderFactory.get()

    val reader = getDatumReader(rawSchema, null)
    return reader.read(null, decoderFactory.binaryDecoder(data, null))

  }
}