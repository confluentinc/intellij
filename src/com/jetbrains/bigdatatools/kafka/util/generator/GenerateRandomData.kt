package com.jetbrains.bigdatatools.kafka.util.generator

import com.google.gson.GsonBuilder
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.core.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaMonitoringToolWindowController
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema

object GenerateRandomData {
  val logger = Logger.getInstance(this::class.java)

  fun generate(project: Project?, config: ConsumerProducerFieldConfig): String = try {
    generateData(project, config)
  }
  catch (_: Throwable) {
    val notificationGroup = KafkaMonitoringToolWindowController.getNotificationGroup()
    val notification = notificationGroup.createNotification(KafkaMessagesBundle.message("notification.generate.failed.title"),
                                                            KafkaMessagesBundle.message("notification.generate.failed.text"),
                                                            NotificationType.WARNING)
      .addAction(DumbAwareAction.create(KafkaMessagesBundle.message("notification.create.issue")) {
        BrowserUtil.open("https://youtrack.jetbrains.com/newIssue?project=IJPL")
      })
    notification.notify(project)
    ""
  }

  fun ParsedSchema.isValidSchema(project: Project?): Boolean = try {
    this.validate()
    true
  }
  catch (e: Exception) {
    RfsNotificationUtils.showErrorMessage(project,
                                          KafkaMessagesBundle.message("schema.invalid.text", this.name()) + "\n${e.cause?.message}",
                                          KafkaMessagesBundle.message("schema.invalid.title"))
    false
  }

  private fun generateData(project: Project?, config: ConsumerProducerFieldConfig): String {
    val parsedSchema = config.parsedSchema
    return when (config.type) {
      KafkaFieldType.STRING -> PrimitivesGenerator.generateString()
      KafkaFieldType.LONG -> PrimitivesGenerator.generateLong().toString()
      KafkaFieldType.INTEGER -> PrimitivesGenerator.generateInt().toString()
      KafkaFieldType.DOUBLE -> PrimitivesGenerator.generateDouble().toString()
      KafkaFieldType.FLOAT -> PrimitivesGenerator.generateFloat().toString()
      KafkaFieldType.BASE64 -> PrimitivesGenerator.generateBytesBase64()
      KafkaFieldType.SCHEMA_REGISTRY -> {
        val schemaType = parsedSchema?.schemaType()
        when (KafkaRegistryFormat.parse(schemaType ?: error("Schema is not provided for generation data"))) {
          KafkaRegistryFormat.AVRO -> AvroGenerator.generateAvroMessage(project, parsedSchema)
          KafkaRegistryFormat.PROTOBUF -> ProtobufGenerator.generateProtobufMessage(project, parsedSchema)
          KafkaRegistryFormat.JSON -> JsonSchemaGenerator.generateJsonMessage(project, parsedSchema,
                                                                              config.registryType == KafkaRegistryType.CONFLUENT)
          KafkaRegistryFormat.UNKNOWN -> error("Schema is unknown for $parsedSchema")
        }
      }
      KafkaFieldType.JSON -> GsonBuilder().setPrettyPrinting().create().toJson(JsonGenerator.generateJson())
      KafkaFieldType.NULL -> ""
      KafkaFieldType.PROTOBUF_CUSTOM -> ProtobufGenerator.generateProtobufMessage(project, parsedSchema)
      KafkaFieldType.AVRO_CUSTOM -> AvroGenerator.generateAvroMessage(project, parsedSchema)
    }
  }
}