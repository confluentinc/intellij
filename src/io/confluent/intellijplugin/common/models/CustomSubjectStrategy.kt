package io.confluent.intellijplugin.common.models

import io.confluent.intellijplugin.schemaregistry.ParsedSchema
import io.confluent.intellijplugin.serializers.subject.strategy.SubjectNameStrategy

class CustomSubjectStrategy(val schemaName: String) : SubjectNameStrategy {
  override fun configure(configs: MutableMap<String, *>?) {}

  override fun subjectName(topic: String?, isKey: Boolean, schema: ParsedSchema?): String = schemaName
}