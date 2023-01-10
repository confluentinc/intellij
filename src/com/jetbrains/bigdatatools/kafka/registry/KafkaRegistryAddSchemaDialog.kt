package com.jetbrains.bigdatatools.kafka.registry

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.doOnChange
import com.jetbrains.bigdatatools.common.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryInfo
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaRegistrySchemaEditor
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * TODO: @nikita.pavlenko
 * - add more pretty validation message place
 * - rewrite validation logic if reqired, seems that parse new cache values perform after validation
 * - disable ok if schema is not parsed
 * - check conductor ui, maybe we need change comboboxes to radioboxes and remove or rewrite some fields
 */
class KafkaRegistryAddSchemaDialog(private val project: Project,
                                   val dataManager: KafkaDataManager) : DialogWrapper(project, true, IdeModalityType.PROJECT) {
  private val formatCombobox = ComboBox(KafkaRegistryFormat.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryFormat> { it.presentable }
    addItemListener {
      onChangeFormat()
    }
  }

  private val strategyCombobox = ComboBox(KafkaRegistryStrategy.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryStrategy> { it.presentable }
    addItemListener {
      onChangeStrategy()
    }
  }

  private val keyValueCombobox = ComboBox(KafkaRegistryKeyValue.values()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryKeyValue> { it.presentable }
    addItemListener {
      onChangeStrategy()
    }
  }

  private val topicField = KafkaEditorUtils.createTopicComboBox(disposable, dataManager).also {
    it.addItemListener {
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

  // In case of proto - ProtoBaseLanguage
  private var jsonTextArea = createEditor(project, true)
  private val textScrollPane = JPanel(BorderLayout()).apply {
    add(jsonTextArea, BorderLayout.CENTER)
  }

  private lateinit var errorLabel: JEditorPane

  // Error will be shown only after user first time tries to save schema.
  private var okButtonPressed = false

  //= JLabel().apply {
  //  isOpaque = true
  //  isEnabled = false
  //  isVisible = false
  //  verticalTextPosition = SwingConstants.TOP
  //  border = JBUI.Borders.empty(10, 15, 15, 15)
  //  background = LightColors.RED
  //}

  private val panel = panel {
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.format")) { cell(formatCombobox) }
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.strategy")) { cell(strategyCombobox) }
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.key.value")) { cell(keyValueCombobox) }.visibleIf(
      keyValueVisible)
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.topic")) { cell(topicField) }.visibleIf(topicFieldVisible)
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.record")) { cell(recordField) }.visibleIf(recordFieldVisible)
    row(subjectNameLabel) { cell(subjectNameField) }
    row { cell(textScrollPane).align(Align.FILL).resizableColumn() }.resizableRow()
    row { /*cell(errorLabel).align(Align.FILL).resizableColumn() */ errorLabel = comment("").component }
  }

  init {
    init()
    title = KafkaMessagesBundle.message("registry.add.schema.dialog.title")
    onChangeFormat()
  }

  private fun createEditor(project: Project, isJson: Boolean) = KafkaRegistrySchemaEditor.createEditor(project, isJson).apply {
    setDisposedWith(disposable)
  }.also {
    it.document.doOnChange {
      onChangeStrategy()
    }
  }

  fun applyRegistryInfo(registryInfo: SchemaRegistryInfo) {
    formatCombobox.selectedItem = KafkaRegistryFormat.valueOf(registryInfo.type)
    jsonTextArea.text = KafkaEditorUtils.toPrettyJson(registryInfo.schema)
  }

  override fun createCenterPanel(): JComponent = panel

  override fun getDimensionServiceKey() = "com.jetbrains.bigdatatools.common.ui.add.kafka.registry.dialog.bounds"

  fun getSchemaName(): String = subjectNameField.text
  fun getParsedSchema(): ParsedSchema = KafkaRegistryUtil.parseSchema(getFormat(), jsonTextArea.text)

  //override fun isOKActionEnabled(): Boolean {
  //  return cachedNotificationError == null
  //}

  private fun onChangeFormat() {
    jsonTextArea = createEditor(project, formatCombobox.item != KafkaRegistryFormat.PROTOBUF)
    textScrollPane.removeAll()
    textScrollPane.add(jsonTextArea, BorderLayout.CENTER)

    val newDefault = KafkaRegistryTemplates.getDefaultIfNotConfigured(jsonTextArea.text, getFormat())
    newDefault?.let {
      jsonTextArea.text = it
    }
    onChangeStrategy()
  }

  private fun onChangeStrategy() {
    updateParsedSchema()

    when (getStrategy()) {
      KafkaRegistryStrategy.CUSTOM -> {
        subjectNameField.isEditable = true
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.custom.name")
        keyValueVisible.set(false)
        topicFieldVisible.set(false)
        recordFieldVisible.set(false)
      }
      KafkaRegistryStrategy.TOPIC_NAME -> {
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        keyValueVisible.set(true)
        topicFieldVisible.set(true)
        recordFieldVisible.set(false)

        subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + keyValueCombobox.item.name.lowercase()
      }
      KafkaRegistryStrategy.RECORD_NAME -> {
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        keyValueVisible.set(false)
        topicFieldVisible.set(false)
        recordFieldVisible.set(true)

        updateRecordFieldText()
        subjectNameField.text = recordField.text
      }
      KafkaRegistryStrategy.TOPIC_RECORD_NAME -> {
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        keyValueVisible.set(true)
        topicFieldVisible.set(true)
        recordFieldVisible.set(false)

        updateRecordFieldText()
        subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + recordField.text?.ifBlank { "<record>" }
      }
    }
    val newDefault = KafkaRegistryTemplates.getDefaultIfNotConfigured(jsonTextArea.text, getFormat())
    newDefault?.let {
      jsonTextArea.text = it
    }
  }

  private fun updateRecordFieldText() {
    val newRecordName = KafkaRegistryUtil.parseRecordName(cachedParsedSchema) ?: ""
    if (recordField.text != newRecordName) {
      recordField.text = newRecordName
    }
  }

  private fun getFormat() = (formatCombobox.selectedItem as KafkaRegistryFormat).name
  private fun getStrategy() = (strategyCombobox.selectedItem as KafkaRegistryStrategy)

  private fun updateParsedSchema() {
    if (!okButtonPressed) {
      return
    }

    try {
      cachedParsedSchema = getParsedSchema()
      isOKActionEnabled = true
      errorLabel.isVisible = false
      //cachedNotificationError = null
    }
    catch (t: Throwable) {
      cachedParsedSchema = null
      //cachedNotificationError = t
      isOKActionEnabled = false
      errorLabel.isVisible = true
      errorLabel.text = t.toPresentableText() //"<html>${t.toPresentableText()}</html>"
    }
  }

  override fun doOKAction() {
    if (!okButtonPressed) {
      okButtonPressed = true
      updateParsedSchema()
    }

    if (okAction.isEnabled) {
      applyFields()
      close(OK_EXIT_CODE)
    }
  }
}