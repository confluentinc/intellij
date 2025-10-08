package io.confluent.intellijplugin.registry

import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.*
import java.io.*

object KafkaRegistryJsonSerDe {
    fun jsonToAvro(json: String, schema: Schema): GenericRecord? {
        val input = ByteArrayInputStream(json.toByteArray())
        input.use {
            val reader: DatumReader<GenericRecord?> = GenericDatumReader(schema)
            val din = DataInputStream(input)
            val decoder: Decoder = DecoderFactory.get().jsonDecoder(schema, din)
            return reader.read(null, decoder)
        }
    }

    fun avroToJson(avro: ByteArray?, schemaStr: String?): String? {
        val pretty = false
        var reader: GenericDatumReader<GenericRecord?>? = null
        var encoder: JsonEncoder? = null
        var output: ByteArrayOutputStream? = null
        return try {
            val schema = Schema.Parser().parse(schemaStr)
            reader = GenericDatumReader(schema)
            val input: InputStream = ByteArrayInputStream(avro)
            output = ByteArrayOutputStream()
            val writer: DatumWriter<GenericRecord?> = GenericDatumWriter(schema)
            encoder = EncoderFactory.get().jsonEncoder(schema, output, pretty)
            val decoder: Decoder = DecoderFactory.get().binaryDecoder(input, null)
            var datum: GenericRecord?
            while (true) {
                datum = try {
                    reader.read(null, decoder)
                } catch (eofe: EOFException) {
                    break
                }
                writer.write(datum, encoder)
            }
            encoder.flush()
            output.flush()
            String(output.toByteArray())
        } finally {
            try {
                output?.close()
            } catch (e: Exception) {
            }
        }
    }
}