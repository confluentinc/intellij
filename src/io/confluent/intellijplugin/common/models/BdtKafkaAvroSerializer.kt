package io.confluent.intellijplugin.common.models

import io.confluent.intellijplugin.schemaregistry.ParsedSchema
import io.confluent.intellijplugin.schemaregistry.avro.AvroSchema
import io.confluent.intellijplugin.schemaregistry.client.SchemaRegistryClient
import io.confluent.intellijplugin.serializers.KafkaAvroSerializer

class BdtKafkaAvroSerializer(client: SchemaRegistryClient?, schemaName: String, val parsedSchema: ParsedSchema?) : KafkaAvroSerializer(
  client) {
  init {
    keySubjectNameStrategy = CustomSubjectStrategy(schemaName)
    valueSubjectNameStrategy = CustomSubjectStrategy(schemaName)
  }

  override fun getSubjectName(topic: String?, isKey: Boolean, value: Any?, schema: ParsedSchema?): String {
    val refs = parsedSchema?.references() ?: return super.getSubjectName(topic, isKey, value, schema)
    if (refs.isEmpty())
      return super.getSubjectName(topic, isKey, value, schema)

    val canonicalString = schema?.canonicalString()

    val resolvedName = (parsedSchema as AvroSchema).resolvedReferences().entries.firstOrNull {
      it.value == canonicalString
    }?.key

    val resolvedSubject = refs.firstOrNull { it.name == resolvedName }?.subject
    return resolvedSubject ?: super.getSubjectName(topic, isKey, value, schema)
  }
}