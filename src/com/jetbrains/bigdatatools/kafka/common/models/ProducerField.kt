package com.jetbrains.bigdatatools.kafka.common.models

import com.jetbrains.bigdatatools.kafka.registry.ConfluentRegistryStrategy
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import io.confluent.kafka.serializers.subject.RecordNameStrategy
import io.confluent.kafka.serializers.subject.TopicNameStrategy
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy
import java.util.*


data class ProducerField(val type: FieldType,
                         val text: String?,
                         val strategy: ConfluentRegistryStrategy?,
                         val parsedSchema: ParsedSchema?,
                         val schemaName: String,
                         val registryName: String) {
  val registryStrategy: SubjectNameStrategy? = when {
    type !in FieldType.registryValues -> null
    strategy == ConfluentRegistryStrategy.TOPIC_NAME -> TopicNameStrategy()
    strategy == ConfluentRegistryStrategy.RECORD_NAME -> RecordNameStrategy()
    strategy == ConfluentRegistryStrategy.TOPIC_RECORD_NAME -> TopicRecordNameStrategy()
    else -> null
  }

  val value: Any? = when (type) {
    FieldType.JSON -> text
    FieldType.STRING -> text
    FieldType.LONG -> text?.toLong()
    FieldType.DOUBLE -> text?.toDouble()
    FieldType.FLOAT -> text?.toFloat()
    FieldType.BASE64 -> text?.let { Base64.getDecoder().decode(it) }
    FieldType.NULL -> null
    FieldType.AVRO_REGISTRY -> AvroSchemaUtils.toObject(text, parsedSchema as AvroSchema)
    FieldType.PROTOBUF_REGISTRY -> ProtobufSchemaUtils.toObject(text, parsedSchema as ProtobufSchema)
    FieldType.JSON_REGISTRY -> JsonSchemaUtils.toObject(text, parsedSchema as JsonSchema)
  }
}