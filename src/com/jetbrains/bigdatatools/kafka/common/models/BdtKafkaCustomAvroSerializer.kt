package com.jetbrains.bigdatatools.kafka.common.models

import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.AbstractKafkaAvroSerializer
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Schema
import org.apache.avro.io.DatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class BdtKafkaCustomAvroSerializer(private val producerConfig: ConsumerProducerFieldConfig) : Serializer<Any>, AbstractKafkaAvroSerializer() {
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
          "Unrecognized bytes object of type: " + value.javaClass.getName())
      }
    }
    else {
      writeDatum(out, value, rawSchema)
    }
    return out.toByteArray()
  }

  @Throws(IOException::class)
  private fun writeDatum(out: ByteArrayOutputStream, value: Any, rawSchema: Schema) {
    val encoder = encoderFactory.directBinaryEncoder(out, null)

    @Suppress("UNCHECKED_CAST")
    val writer = datumWriterCache.computeIfAbsent(rawSchema
    ) { v: Schema? ->
      getDatumWriter(value, rawSchema) as DatumWriter<Any>
    }
    writer.write(value, encoder)
    encoder.flush()
  }
}