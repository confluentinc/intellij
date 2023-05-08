package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowsRange
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.getValidationInfo
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.SimpleDumbAwareAction
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaConsumerFieldComponent(private val project: Project,
                                  private val consumerPanel: KafkaConsumerPanel, val isKey: Boolean) {
  private val kafkaManager = consumerPanel.kafkaManager

  private val fieldTypes = if (consumerPanel.kafkaManager.registryType != KafkaRegistryType.NONE)
    FieldType.allValues
  else
    FieldType.defaultValues

  val fieldTypeComboBox = ComboBox(fieldTypes.toTypedArray<FieldType>()).apply<ComboBox<FieldType>> {
    renderer = CustomListCellRenderer<FieldType> { it.title }
    selectedItem = FieldType.STRING
    addActionListener {
      consumerPanel.updateVisibility()
      consumerPanel.storeToUserData()
    }
  }

  val schemaComboBox = KafkaEditorUtils.createSchemaComboBox(
    consumerPanel,
    consumerPanel.kafkaManager,
    consumerPanel.topicComboBox,
    fieldTypeComboBox,
    isKey)

  private lateinit var registryRows: RowsRange

  fun createComponent(panel: Panel) {
    val title = if (isKey)
      KafkaMessagesBundle.message("consumer.producer.key.group")
    else
      KafkaMessagesBundle.message("consumer.producer.value.group")

    panel.apply {
      group(title) {
        row(KafkaMessagesBundle.message("consumer.producer.format.type")) { cell(fieldTypeComboBox).align(Align.FILL).resizableColumn() }
        registryRows = rowsRange {
          row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
            cell(schemaComboBox).align(Align.FILL).resizableColumn().gap(RightGap.SMALL)
            actionButton(SimpleDumbAwareAction(KafkaMessagesBundle.message("show.schema.info"),
                                               AllIcons.Actions.ToggleVisibility) {
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
          }
        }
      }
    }
    updateRegistryFieldsVisibility()
  }

  private fun updateRegistryFieldsVisibility() {
    val isRegistryField = fieldTypeComboBox.item in FieldType.registryValues
    registryRows.visible(isRegistryField)
    if (!isRegistryField)
      return
  }

  fun load(config: StorageConsumerConfig) {
    fieldTypeComboBox.item = if (isKey) config.getKeyType() else config.getValueType()

    schemaComboBox.item = if (isKey)
      RegistrySchemaInEditor(schemaName = config.keySubject, registryName = config.keyRegistry)
    else
      RegistrySchemaInEditor(schemaName = config.valueSubject, registryName = config.valueRegistry)
  }

  fun updateIsEnabled(isEnabled: Boolean) {
    fieldTypeComboBox.isEnabled = isEnabled
    schemaComboBox.isEnabled = isEnabled

    updateRegistryFieldsVisibility()
  }

  fun loadFieldConfig(): ConsumerProducerFieldConfig {
    val fieldType = fieldTypeComboBox.item
    val registryType = kafkaManager.registryType
    val schemaName = schemaComboBox.item?.schemaName ?: ""
    val schema = KafkaRegistryUtil.loadSchema(registryType, schemaName, fieldType, kafkaManager)

    return ConsumerProducerFieldConfig(type = fieldType,
                                       valueText = "",
                                       isKey = isKey,
                                       topic = consumerPanel.topicComboBox.item.name,
                                       registryType = registryType,
                                       schemaName = schemaName,
                                       parsedSchema = schema)
  }

  fun getValidationInfo() = schemaComboBox.getValidationInfo()
}