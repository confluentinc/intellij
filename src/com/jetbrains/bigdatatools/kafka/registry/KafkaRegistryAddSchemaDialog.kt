package com.jetbrains.bigdatatools.kafka.registry

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryInfo
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaRegistrySchemaEditor
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextField

class KafkaRegistryAddSchemaDialog(project: Project, val dataManager: KafkaDataManager) :
  DialogWrapper(project, false, IdeModalityType.MODELESS) {

  private val formatCombobox = ComboBox(KafkaRegistryFormat.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryFormat> { it.presentable }
    addActionListener {
      onChangeFormat()
    }
  }

  private val strategyCombobox = ComboBox(KafkaRegistryStrategy.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryStrategy> { it.presentable }
    addActionListener {
      onChangeStrategy()
    }
  }

  private val keyValueCombobox = ComboBox(KafkaRegistryKeyValue.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryKeyValue> { it.presentable }
    addActionListener {
      onChangeStrategy()
    }
  }

  private val topicField = KafkaEditorUtils.createTopicComboBox(disposable, dataManager).also {
    it.addActionListener {
      onChangeStrategy()
    }
  }

  private val recordField = JTextField("").also {
    it.isEditable = false
  }

  private val subjectNameField = JTextField("")
  private val subjectNameLabel = JBLabel("")

  private var cachedParsedSchema: ParsedSchema? = null

  private val keyValueVisible = AtomicBooleanProperty(false)
  private val topicFieldVisible = AtomicBooleanProperty(false)
  private val recordFieldVisible = AtomicBooleanProperty(false)
  private val subjectFieldVisible = AtomicBooleanProperty(false)

  private val textScrollPane = KafkaRegistrySchemaEditor(project) {
    updateParsedSchema()
    updateRecordFieldText()
  }

  private lateinit var errorRow: Row
  private lateinit var errorLabel: JEditorPane

  // Error will be shown only after user first time tries to save schema.
  private var okButtonPressed = false

  private val panel = panel {
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.format")) {
      cell(formatCombobox)
      label(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.strategy"))
      cell(strategyCombobox)
    }

    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.key.value")) { cell(keyValueCombobox) }.visibleIf(
      keyValueVisible)
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.topic")) { cell(topicField) }.visibleIf(topicFieldVisible)
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.record")) {
      cell(recordField).align(Align.FILL).resizableColumn()
    }.visibleIf(recordFieldVisible)
    row(subjectNameLabel) { cell(subjectNameField).align(Align.FILL).resizableColumn() }.visibleIf(subjectFieldVisible)
    row { cell(textScrollPane.component).align(Align.FILL).resizableColumn() }.resizableRow()
    errorRow = row {
      label("").component.icon = AllIcons.General.Error; errorLabel = comment("").component.apply {
      foreground = UIUtil.getLabelForeground()
    }
    }
    errorRow.visible(false)
  }

  init {
    init()
    title = KafkaMessagesBundle.message("registry.add.schema.dialog.title")
    onChangeFormat()
    onChangeStrategy()
  }

  fun applyRegistryInfo(registryInfo: SchemaRegistryInfo) {
    formatCombobox.selectedItem = KafkaRegistryFormat.valueOf(registryInfo.type)
    textScrollPane.setText(KafkaEditorUtils.toPrettyJson(registryInfo.schema),
                           isJson = formatCombobox.selectedItem != KafkaRegistryFormat.PROTOBUF)
  }

  override fun createCenterPanel(): JComponent = panel

  override fun getDimensionServiceKey() = "com.jetbrains.bigdatatools.common.ui.add.kafka.registry.dialog.bounds"

  private fun getSchemaName(): String = subjectNameField.text

  private fun onChangeFormat() {
    val newDefault = KafkaRegistryTemplates.getDefaultIfNotConfigured(textScrollPane.text, getFormat())
    newDefault?.let {
      textScrollPane.setText(it, isJson = formatCombobox.selectedItem != KafkaRegistryFormat.PROTOBUF)
    }
    updateRecordFieldText()
  }

  private fun onChangeStrategy() {
    updateParsedSchema()

    when (getStrategy()) {
      KafkaRegistryStrategy.CUSTOM -> {
        subjectFieldVisible.set(true)
        subjectNameField.isEditable = true
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.custom.name")
        keyValueVisible.set(false)
        topicFieldVisible.set(false)
        recordFieldVisible.set(false)
      }
      KafkaRegistryStrategy.TOPIC_NAME -> {
        subjectFieldVisible.set(true)
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        keyValueVisible.set(true)
        topicFieldVisible.set(true)
        recordFieldVisible.set(false)

        subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + keyValueCombobox.item.name.lowercase()
      }
      KafkaRegistryStrategy.RECORD_NAME -> {
        subjectFieldVisible.set(false)
        keyValueVisible.set(false)
        topicFieldVisible.set(false)
        recordFieldVisible.set(true)

        updateRecordFieldText()
      }
      KafkaRegistryStrategy.TOPIC_RECORD_NAME -> {
        subjectFieldVisible.set(true)
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        keyValueVisible.set(true)
        topicFieldVisible.set(true)
        recordFieldVisible.set(false)

        updateRecordFieldText()
        subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + recordField.text?.ifBlank { "<record>" }
      }
    }
  }

  private fun updateRecordFieldText() {
    val strategy = getStrategy()
    if (strategy == KafkaRegistryStrategy.RECORD_NAME) {
      val newRecordName = KafkaRegistryUtil.parseRecordName(cachedParsedSchema) ?: ""
      if (recordField.text != newRecordName) {
        recordField.text = newRecordName
      }
    }

    if (strategy == KafkaRegistryStrategy.TOPIC_RECORD_NAME) {
      val newRecordName = KafkaRegistryUtil.parseRecordName(cachedParsedSchema) ?: ""
      subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + newRecordName.ifBlank { "<record>" }
    }
  }

  private fun getFormat(): String = formatCombobox.item.name
  private fun getStrategy(): KafkaRegistryStrategy = strategyCombobox.item

  private fun updateParsedSchema() {
    val parsedSchemaResult = KafkaRegistryUtil.parseSchema(getFormat(), textScrollPane.text)

    if (parsedSchemaResult.isSuccess) {
      cachedParsedSchema = parsedSchemaResult.getOrNull()
      if (okButtonPressed) {
        isOKActionEnabled = true
        errorRow.visible(false)
      }
    }
    else {
      cachedParsedSchema = null
      if (okButtonPressed) {
        isOKActionEnabled = false
        errorLabel.text = parsedSchemaResult.exceptionOrNull()?.toPresentableText()
        errorRow.visible(true)
      }
    }
  }

  override fun doOKAction() {
    if (!okButtonPressed) {
      okButtonPressed = true
      updateParsedSchema()
    }

    if (okAction.isEnabled) {
      val schemaName = getSchemaName()
      val parsedSchema = KafkaRegistryUtil.parseSchema(getFormat(), textScrollPane.text).getOrNull() ?: return

      dataManager.createRegistrySubject(schemaName, parsedSchema).onError {
        runInEdt {
          errorLabel.text = it.message
          errorRow.visible(true)
        }
      }.onSuccess {
        runInEdt {
          close(OK_EXIT_CODE)
        }
      }
    }
  }
}