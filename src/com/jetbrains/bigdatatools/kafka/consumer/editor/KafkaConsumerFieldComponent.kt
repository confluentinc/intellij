package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBTextField
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.SubjectInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryConsumerType

class KafkaConsumerFieldComponent(private val consumerPanel: KafkaConsumerPanel, val isKey: Boolean) {
  val typeComboBox = createFileTypeCombobox {
    if (isKey)
      consumerPanel.details.keyType = it
    else
      consumerPanel.details.valueType = it

    registryType.isVisible = it in FieldType.registryValues
    updateSubjectVisibility()
  }

  val registryType = createFieldTypeComboBox().apply {
    addItemListener {
      updateSubjectVisibility()
    }
  }

  val subjectComboBox = KafkaEditorUtils.createSubjectComboBox(consumerPanel, consumerPanel.kafkaManager)
  val schemaIdField = JBTextField(15)

  val schemaJsonField: EditorTextField by lazy {
    KafkaEditorUtils.createJsonTextArea(consumerPanel.project).apply {
      setDisposedWith(consumerPanel)
    }
  }

  private fun updateSubjectVisibility() {
    registryType.isVisible = typeComboBox.item in FieldType.registryValues
    subjectComboBox.isVisible = registryType.isVisible && registryType.item == KafkaRegistryConsumerType.SUBJECT
    schemaIdField.isVisible = registryType.isVisible && registryType.item == KafkaRegistryConsumerType.SCHEMA_ID
    schemaJsonField.isVisible = registryType.isVisible && registryType.item == KafkaRegistryConsumerType.CUSTOM
  }

  private fun createFileTypeCombobox(onChange: (FieldType) -> Unit): ComboBox<FieldType> {
    val registryTypes = if (consumerPanel.kafkaManager.isKafkaRegistryEnabled) {
      FieldType.registryValues
    }
    else {
      emptyList()
    }
    val fieldTypes = FieldType.defaultValues + registryTypes
    return ComboBox(fieldTypes.toTypedArray()).apply {
      renderer = CustomListCellRenderer<FieldType> { it.title }
      selectedItem = FieldType.STRING
      addItemListener {
        consumerPanel.updateVisibility()
        consumerPanel.storeToFile()
        if (consumerPanel.detailsDelegate.isInitialized()) {
          onChange(item)
        }
      }
    }
  }

  private fun createFieldTypeComboBox() = ComboBox(KafkaRegistryConsumerType.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryConsumerType> { it.presentable }
    selectedItem = FieldType.STRING
    addItemListener {
      consumerPanel.updateVisibility()
    }
  }

  fun load(config: StorageConsumerConfig) {
    typeComboBox.item = if (isKey) config.getKeyType() else config.getValueType()
    registryType.item = if (isKey) config.getKeyRegistryType() else config.getValueRegistryType()
    subjectComboBox.item = if (isKey) SubjectInEditor(config.keySubject) else SubjectInEditor(config.valueSubject)
    schemaIdField.text = if (isKey) config.keySchemaId else config.valueSchemaId
    schemaJsonField.text = if (isKey) config.keyCustomSchema else config.valueCustomSchema
  }

  fun updateIsEnabled(isEnabled: Boolean) {
    typeComboBox.isEnabled = isEnabled
    registryType.isEnabled = isEnabled
    subjectComboBox.isEnabled = isEnabled
    schemaIdField.isEnabled = isEnabled
    schemaJsonField.isEnabled = isEnabled

    updateSubjectVisibility()
  }
}