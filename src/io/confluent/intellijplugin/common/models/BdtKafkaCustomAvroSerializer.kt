package io.confluent.intellijplugin.common.models

import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.DatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class BdtKafkaCustomAvroSerializer(private val producerConfig: ConsumerProducerFieldConfig) : Serializer<Any>,
    AbstractKafkaAvroSerializer() {
    private val encoderFactory = EncoderFactory.get()
    private val datumWriterCache: ConcurrentHashMap<Schema, DatumWriter<Any>> = ConcurrentHashMap()

    override fun serialize(topic: String?, data: Any): ByteArray {
        val schema = producerConfig.parsedSchema as AvroSchema

        val out = ByteArrayOutputStream()
        val value = if (data is NonRecordContainer) data.value else data
        val rawSchema: Schema = schema.rawSchema()
        if (rawSchema.type == Schema.Type.BYTES) {
            when (value) {
                is ByteArray -> out.write(value)
                is ByteBuffer -> out.write(value.array())
                else -> throw SerializationException(
                    "Unrecognized bytes object of type: " + value.javaClass.getName()
                )
            }
        } else {
            writeDatum(out, value, rawSchema)
        }
        return out.toByteArray()
    }

    override fun close() {}

    @Throws(IOException::class)
    private fun writeDatum(out: ByteArrayOutputStream, value: Any, rawSchema: Schema) {
        val encoder = encoderFactory.directBinaryEncoder(out, null)

        @Suppress("UNCHECKED_CAST")
        val writer = datumWriterCache.computeIfAbsent(
            rawSchema
        ) { schema: Schema ->
            if (value is SpecificRecord) {
                SpecificDatumWriter<Any>(schema)
            } else {
                GenericDatumWriter<Any>(schema)
            }
        }
        writer.write(value, encoder)
        encoder.flush()
    }
}