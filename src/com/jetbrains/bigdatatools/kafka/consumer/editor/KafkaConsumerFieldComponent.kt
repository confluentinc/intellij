package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.layout.selectedValueMatches
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.getValidationInfo
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.producer.editor.CustomSchemaController
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaConsumerFieldComponent(private val project: Project,
                                  private val consumerPanel: KafkaConsumerPanel, val isKey: Boolean) : Disposable {
  private val kafkaManager = consumerPanel.kafkaManager

  private val customSchemaController = CustomSchemaController(project, isKey).also { Disposer.register(this, it) }


  val fieldTypeComboBox = KafkaEditorUtils.createFieldTypeComboBox(consumerPanel.topicComboBox, consumerPanel.kafkaManager, isKey) {
    consumerPanel.updateVisibility()
    consumerPanel.storeToUserData()
    customSchemaController.setLanguage(it.item)
  }
  private val customSchemaPredicate = fieldTypeComboBox.selectedValueMatches {
    it in setOf(KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM)
  }

  val schemaComboBox = KafkaEditorUtils.createSchemaComboBox(
    consumerPanel,
    consumerPanel.kafkaManager,
    consumerPanel.topicComboBox,
    isKey)


  init {
    customSchemaController.setLanguage(fieldTypeComboBox.item)
  }

  override fun dispose() {}

  fun createComponent(panel: Panel) {
    val title = if (isKey)
      KafkaMessagesBundle.message("consumer.producer.key.group")
    else
      KafkaMessagesBundle.message("consumer.producer.value.group")

    panel.apply {
      group(title) {
        row(KafkaMessagesBundle.message("consumer.producer.format.type")) { cell(fieldTypeComboBox).align(Align.FILL).resizableColumn() }
        rowsRange {
          row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
            cell(schemaComboBox).align(Align.FILL).resizableColumn().gap(RightGap.SMALL)
            actionButton(DumbAwareAction.create(KafkaMessagesBundle.message("show.schema.info"), AllIcons.Actions.ToggleVisibility) {
              executeNotOnEdt {
                try {
                  val config = loadFieldConfig()
                  val schema = config.parsedSchema ?: return@executeNotOnEdt
                  KafkaSchemaInfoDialog.show(project = project, schemaType = schema.schemaType(),
                                             schemaDefinition = schema.canonicalString(),
                                             schemaName = config.schemaName)
                }
                catch (t: Throwable) {
                  RfsNotificationUtils.showExceptionMessage(project, t)
                }
              }
            })
          }.visibleIf(fieldTypeComboBox.selectedValueMatches { it == KafkaFieldType.SCHEMA_REGISTRY })
        }
        customSchemaController.initComponent(this).visibleIf(customSchemaPredicate)
      }
    }
  }

  fun load(config: StorageConsumerConfig) {
    fieldTypeComboBox.item = if (isKey) config.getKeyType() else config.getValueType()
    customSchemaController.setConfig(config)
    schemaComboBox.item = if (isKey)
      RegistrySchemaInEditor(schemaName = config.keySubject, schemaFormat = config.getKeyFormat())
    else
      RegistrySchemaInEditor(schemaName = config.valueSubject, schemaFormat = config.getValueFormat())
  }

  fun updateIsEnabled(isEnabled: Boolean) {
    fieldTypeComboBox.isEnabled = isEnabled
    schemaComboBox.isEnabled = isEnabled
  }

  fun loadFieldConfig(): ConsumerProducerFieldConfig {
    val fieldType = fieldTypeComboBox.item
    val registryType = kafkaManager.registryType
    val schemaName = schemaComboBox.item?.schemaName ?: ""
    val schema = when (fieldType) {
      null, KafkaFieldType.STRING, KafkaFieldType.JSON, KafkaFieldType.LONG,
      KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64, KafkaFieldType.NULL -> null
      KafkaFieldType.SCHEMA_REGISTRY -> KafkaRegistryUtil.loadSchema(schemaName, fieldType, kafkaManager)
      KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM -> customSchemaController.getSchema()
    }

    return ConsumerProducerFieldConfig(type = fieldType,
                                       valueText = "",
                                       isKey = isKey,
                                       topic = consumerPanel.topicComboBox.item?.name ?: "",
                                       registryType = registryType,
                                       schemaName = schemaName,
                                       schemaFormat = KafkaRegistryFormat.parse(schema?.schemaType()),
                                       parsedSchema = schema)
  }

  fun getValidationInfo() = schemaComboBox.getValidationInfo()
}