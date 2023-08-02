package com.jetbrains.bigdatatools.kafka.util.generator

import com.google.gson.GsonBuilder
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaMonitoringToolWindowController
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema

object GenerateRandomData {
  val logger = Logger.getInstance(this::class.java)

  fun generate(project: Project?, config: ConsumerProducerFieldConfig): String = try {
    generate(config.type, config.parsedSchema)
  }
  catch (_: Throwable) {
    val notificationGroup = KafkaMonitoringToolWindowController.getNotificationGroup()
    val notification = notificationGroup.createNotification(KafkaMessagesBundle.message("notification.generate.failed.title"),
                                                            KafkaMessagesBundle.message("notification.generate.failed.text"),
                                                            NotificationType.WARNING)
      .addAction(DumbAwareAction.create(MessagesBundle.message("feedback.youtrack")) {
        BrowserUtil.open("https://youtrack.jetbrains.com/newIssue?project=BDIDE")
      })
      .addAction(DumbAwareAction.create(MessagesBundle.message("feedback.slack")) {
        BrowserUtil.open("https://slack-bdt.mau.jetbrains.com/?_ga=2.181253743.913531920.1594027385-1936946878.1588841666")
      })

    notification.notify(project)
    ""
  }

  private fun generate(fieldType: KafkaFieldType, parsedSchema: ParsedSchema?): String = when (fieldType) {
    KafkaFieldType.STRING -> PrimitivesGenerator.generateString()
    KafkaFieldType.LONG -> PrimitivesGenerator.generateLong().toString()
    KafkaFieldType.DOUBLE -> PrimitivesGenerator.generateDouble().toString()
    KafkaFieldType.FLOAT -> PrimitivesGenerator.generateFloat().toString()
    KafkaFieldType.BASE64 -> PrimitivesGenerator.generateBytesBase64()
    KafkaFieldType.SCHEMA_REGISTRY -> {
      val schemaType = parsedSchema?.schemaType()
      when (KafkaRegistryFormat.parse(schemaType ?: error("Schema is not provided for generation data"))) {
        KafkaRegistryFormat.AVRO -> AvroGenerator.generateAvroMessage(parsedSchema)
        KafkaRegistryFormat.PROTOBUF -> ProtobufGenerator.generateProtobufMessage(parsedSchema)
        KafkaRegistryFormat.JSON -> JsonSchemaGenerator.generateJsonMessage(parsedSchema)
        KafkaRegistryFormat.UNKNOWN -> error("Schema is unknown for $parsedSchema")
      }
    }
    KafkaFieldType.JSON -> GsonBuilder().setPrettyPrinting().create().toJson(JsonGenerator.generateJson())
    KafkaFieldType.NULL -> ""
    KafkaFieldType.PROTOBUF_CUSTOM -> ProtobufGenerator.generateProtobufMessage(parsedSchema)
    KafkaFieldType.AVRO_CUSTOM -> AvroGenerator.generateAvroMessage(parsedSchema)
  }
}