package com.jetbrains.bigdatatools.kafka.registry.serde

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.Schema

class BdtKafkaAvroDeserializer : KafkaAvroDeserializer() {
  private var parsedSchema: ParsedSchema? = null

  override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
    super.configure(configs, isKey)

    parsedSchema = if (isKey)
      configs?.get(FieldType.KEY_PARSED_SCHEMA_CONFIG_KEY) as? ParsedSchema
    else
      configs?.get(FieldType.VALUE_PARSED_SCHEMA_CONFIG_KEY) as? ParsedSchema
  }

  override fun deserialize(topic: String?, bytes: ByteArray?): Any? = deserialize(topic, isKey, bytes, parsedSchema?.rawSchema() as? Schema)
}