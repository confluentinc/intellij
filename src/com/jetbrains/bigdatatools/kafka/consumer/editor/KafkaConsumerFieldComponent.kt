package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextField
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.SubjectInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryConsumerType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryTemplates
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaRegistrySchemaEditor
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema

class KafkaConsumerFieldComponent(private val consumerPanel: KafkaConsumerPanel, val isKey: Boolean) {
  val typeComboBox = createFileTypeCombobox {
    if (isKey) {
      consumerPanel.details.keyType = it
    }
    else {
      consumerPanel.details.valueType = it
    }

    registryType.isVisible = it in FieldType.registryValues

    updateSubjectVisibility()
    updateCustomSchemaIfRequired()
  }

  val registryType = createFieldTypeComboBox().apply {
    addActionListener {
      updateSubjectVisibility()
      updateCustomSchemaIfRequired()
    }
  }

  val subjectComboBox = KafkaEditorUtils.createSubjectComboBox(consumerPanel, consumerPanel.kafkaManager).apply { isVisible = false }
  val schemaIdField = JBTextField(15).apply { isVisible = false }

  val customSchemaPanel = KafkaRegistrySchemaEditor(consumerPanel.project)

  /** Switching between JSON/Proto/Avro we are updating default template in editor text. */
  private fun updateCustomSchemaIfRequired() {
    if (!customSchemaPanel.component.isVisible) {
      return
    }

    val provider = when (typeComboBox.item!!) {
      FieldType.AVRO_REGISTRY -> AvroSchema.TYPE
      FieldType.PROTOBUF_REGISTRY -> ProtobufSchema.TYPE
      FieldType.JSON_REGISTRY -> JsonSchema.TYPE
      FieldType.FLOAT, FieldType.BASE64, FieldType.DOUBLE, FieldType.JSON, FieldType.LONG, FieldType.NULL, FieldType.STRING -> null
    }

    provider ?: return

    val newDefault = KafkaRegistryTemplates.getDefaultIfNotConfigured(customSchemaPanel.text, provider)
    newDefault?.let {
      customSchemaPanel.setText(it, isJson = typeComboBox.item != FieldType.PROTOBUF_REGISTRY)
    }
  }

  private fun updateSubjectVisibility() {
    registryType.isVisible = typeComboBox.item in FieldType.registryValues
    subjectComboBox.isVisible = registryType.isVisible && registryType.item == KafkaRegistryConsumerType.SUBJECT
    schemaIdField.isVisible = registryType.isVisible && registryType.item == KafkaRegistryConsumerType.SCHEMA_ID
    customSchemaPanel.component.isVisible = registryType.isVisible && registryType.item == KafkaRegistryConsumerType.CUSTOM
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
      addActionListener {
        consumerPanel.updateVisibility()
        consumerPanel.storeToUserData()
        if (consumerPanel.detailsDelegate.isInitialized()) {
          onChange(item)
        }
      }
    }
  }

  private fun createFieldTypeComboBox() = ComboBox(KafkaRegistryConsumerType.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryConsumerType> { it.presentable }
    prototypeDisplayValue = KafkaRegistryConsumerType.SUBJECT
    selectedItem = KafkaRegistryConsumerType.AUTO
    addActionListener {
      consumerPanel.updateVisibility()
    }
  }

  fun load(config: StorageConsumerConfig) {
    typeComboBox.item = if (isKey) config.getKeyType() else config.getValueType()
    registryType.item = if (isKey) config.getKeyRegistryType() else config.getValueRegistryType()
    subjectComboBox.item = if (isKey) SubjectInEditor(config.keySubject) else SubjectInEditor(config.valueSubject)
    schemaIdField.text = if (isKey) config.keySchemaId else config.valueSchemaId
    customSchemaPanel.setText(if (isKey) config.keyCustomSchema else config.valueCustomSchema,
                              isJson = typeComboBox.item != FieldType.PROTOBUF_REGISTRY)
  }

  fun updateIsEnabled(isEnabled: Boolean) {
    typeComboBox.isEnabled = isEnabled
    registryType.isEnabled = isEnabled
    subjectComboBox.isEnabled = isEnabled
    schemaIdField.isEnabled = isEnabled
    customSchemaPanel.component.isEnabled = isEnabled

    updateSubjectVisibility()
  }
}