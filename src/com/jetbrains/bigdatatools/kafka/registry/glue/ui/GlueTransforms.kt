package com.jetbrains.bigdatatools.kafka.registry.glue.ui

import com.jetbrains.bigdatatools.common.monitoring.data.model.FieldsDataModel
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.services.glue.model.GetSchemaResponse

object GlueTransforms {
  fun getSchemaInfoDetails(schema: GetSchemaResponse): FieldsDataModel {
    val fileds = listOf(
      KafkaMessagesBundle.message("schema.info.name") to schema.schemaName(),
      KafkaMessagesBundle.message("schema.info.arn") to schema.schemaArn(),
      KafkaMessagesBundle.message("schema.info.registry") to schema.registryName(),
      KafkaMessagesBundle.message("schema.info.format") to schema.dataFormatAsString(),
      KafkaMessagesBundle.message("schema.info.compability") to schema.compatibilityAsString(),
      KafkaMessagesBundle.message("schema.info.description") to (schema.description()?.ifBlank { null } ?: "-"),
      KafkaMessagesBundle.message("schema.info.last.updated") to schema.updatedTime(),
      KafkaMessagesBundle.message("schema.info.version") to schema.latestSchemaVersion(),
    ).filter { it.second != null }
    return FieldsDataModel.createForList(fileds)
  }
}