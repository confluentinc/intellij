package com.jetbrains.bigdatatools.kafka.common.models

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy

class CustomSubjectStrategy(val schemaName: String) : SubjectNameStrategy {
  override fun configure(configs: MutableMap<String, *>?) {}

  override fun subjectName(topic: String?, isKey: Boolean, schema: ParsedSchema?): String = schemaName
}