package io.confluent.intellijplugin.common.models

import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.AbstractKafkaAvroDeserializer
import io.confluent.kafka.serializers.schema.id.SchemaId
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.serialization.Deserializer

class BdtKafkaCustomAvroDeserializer(private val producerConfig: ConsumerProducerFieldConfig) :
    AbstractKafkaAvroDeserializer(), Deserializer<Any> {
    override fun deserialize(topic: String?, data: ByteArray?): Any? {
        if (data == null || data.isEmpty())
            return null

        val rawSchema = (producerConfig.parsedSchema as AvroSchema).rawSchema()
        val decoderFactory = DecoderFactory.get()

        // The schema comes from the user's config, not the registry. SchemaId is only a cache key here.
        val reader = getDatumReader(SchemaId(AvroSchema.TYPE), rawSchema, null)
        return reader.read(null, decoderFactory.binaryDecoder(data, null))

    }

    override fun close() {}
}