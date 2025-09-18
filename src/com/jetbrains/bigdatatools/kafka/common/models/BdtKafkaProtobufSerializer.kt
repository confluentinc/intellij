package com.jetbrains.bigdatatools.kafka.common.models

import com.google.protobuf.Message
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer
import io.confluent.kafka.serializers.subject.strategy.ReferenceSubjectNameStrategy

class BdtKafkaProtobufSerializer(client: SchemaRegistryClient?,
                                 schemaName: String,
                                 val parsedSchema: ParsedSchema?) : KafkaProtobufSerializer<Message?>(client) {
  init {
    keySubjectNameStrategy = CustomSubjectStrategy(schemaName)
    valueSubjectNameStrategy = CustomSubjectStrategy(schemaName)
    referenceSubjectNameStrategy = RefStrategy()
  }

  override fun getSubjectName(topic: String?, isKey: Boolean, value: Any?, schema: ParsedSchema?): String {
    val refs = parsedSchema?.references() ?: return super.getSubjectName(topic, isKey, value, schema)
    if (refs.isEmpty())
      return super.getSubjectName(topic, isKey, value, schema)

    val canonicalString = schema?.canonicalString()

    val resolvedName = (parsedSchema as ProtobufSchema).resolvedReferences().entries.firstOrNull {
      it.value == canonicalString
    }?.key

    val resolvedSubject = refs.firstOrNull { it.name == resolvedName }?.subject
    return resolvedSubject ?: super.getSubjectName(topic, isKey, value, schema)
  }

  inner class RefStrategy : ReferenceSubjectNameStrategy {
    override fun configure(configs: MutableMap<String, *>?) {

    }

    override fun subjectName(refName: String, topic: String, isKey: Boolean, schema: ParsedSchema): String {
      val refs = parsedSchema?.references() ?: return refName
      if (refs.isEmpty())
        return refName

      val subject = refs.firstOrNull { it.name == refName }?.subject
      return subject ?: refName
    }
  }
}