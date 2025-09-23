package io.confluent.kafka.registry

import com.intellij.icons.AllIcons
import com.intellij.json.JsonLanguage
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import io.confluent.kafka.common.editor.KafkaEditorUtils
import io.confluent.kafka.common.models.TopicInEditor
import io.confluent.kafka.core.settings.withNonEmptyValidator
import io.confluent.kafka.core.ui.CustomListCellRenderer
import io.confluent.kafka.core.util.toPresentableText
import io.confluent.kafka.data.KafkaDataManager
import io.confluent.kafka.registry.confluent.controller.TopicSchemaViewType
import io.confluent.kafka.registry.ui.KafkaRegistrySchemaEditor
import io.confluent.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextField

class KafkaRegistryAddSchemaDialog(project: Project, val dataManager: KafkaDataManager) :
  DialogWrapper(project, false, IdeModalityType.MODELESS) {
  private val isConfluentSchema = dataManager.connectionData.registryType == KafkaRegistryType.CONFLUENT

  private val formatCombobox = ComboBox((KafkaRegistryFormat.entries - KafkaRegistryFormat.UNKNOWN).toTypedArray()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryFormat> { it.presentable }
    addActionListener {
      onChangeFormat()
    }
  }

  internal val strategyCombobox = ComboBox(ConfluentRegistryStrategy.entries.toTypedArray()).apply {
    renderer = CustomListCellRenderer<ConfluentRegistryStrategy> { it.presentable }
    addActionListener {
      onChangeStrategy()
    }
  }

  internal val keyValueCombobox = ComboBox(KafkaRegistryKeyValue.entries.toTypedArray()).apply {
    renderer = CustomListCellRenderer<KafkaRegistryKeyValue> { it.presentable }
    selectedItem = KafkaRegistryKeyValue.VALUE
    addActionListener {
      onChangeStrategy()
    }
  }

  internal val topicField = KafkaEditorUtils.createTopicComboBox(disposable, dataManager).apply {
    addActionListener {
      onChangeStrategy()
    }
  }

  private val recordField = JTextField("").also {
    it.isEditable = false
  }

  internal val subjectNameField = JTextField("").withNonEmptyValidator(disposable)
  private val subjectNameLabel = JBLabel("")

  private var cachedParsedSchema: ParsedSchema? = null

  private val keyValueVisible = AtomicBooleanProperty(false)
  private val topicFieldVisible = AtomicBooleanProperty(false)
  private val recordFieldVisible = AtomicBooleanProperty(false)
  private val subjectFieldVisible = AtomicBooleanProperty(false)

  private val textScrollPane = KafkaRegistrySchemaEditor(project, disposable) {
    updateParsedSchema()
    updateRecordFieldText()
  }.apply {
    component.border = IdeBorderFactory.createBorder()
  }

  private lateinit var errorRow: Row
  private lateinit var errorLabel: JEditorPane

  // Error will be shown only after user first time tries to save schema.
  private var okButtonPressed = false

  private val panel = panel {
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.format")) {
      cell(formatCombobox)
      if (isConfluentSchema) {
        label(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.strategy"))
        cell(strategyCombobox)
      }
    }

    if (isConfluentSchema) {
      row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.key.value")) { cell(keyValueCombobox) }.visibleIf(
        keyValueVisible)

      row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.topic")) { cell(topicField) }.visibleIf(topicFieldVisible)
      row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.record")) {
        cell(recordField).align(Align.FILL).resizableColumn()
      }.visibleIf(recordFieldVisible)
    }

    row(subjectNameLabel) {
      cell(subjectNameField).align(Align.FILL).resizableColumn().validationOnInput {
        when {
          it.text.isBlank() -> ValidationInfo(KafkaMessagesBundle.message("schema.name.must.be.not.blank"))
          it.text.endsWith("/") -> ValidationInfo(KafkaMessagesBundle.message("schema.name.must.be.not.end.with.slash"))
          else -> null
        }
      }
    }.visibleIf(subjectFieldVisible)

    row { cell(textScrollPane.component).align(Align.FILL).resizableColumn() }.resizableRow()
    errorRow = row {
      label("").component.icon = AllIcons.General.Error
      errorLabel = comment("").component.apply {
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
    if (!isConfluentSchema)
      strategyCombobox.item = ConfluentRegistryStrategy.CUSTOM
  }

  fun applySchemaName(topicName: String, viewType: TopicSchemaViewType) {
    when (viewType) {
      TopicSchemaViewType.DISABLED -> TODO()
      TopicSchemaViewType.KEY -> {
        topicField.item = TopicInEditor(topicName)
        keyValueCombobox.item = KafkaRegistryKeyValue.KEY
        strategyCombobox.item = ConfluentRegistryStrategy.TOPIC_NAME
      }
      TopicSchemaViewType.VALUE -> {
        topicField.item = TopicInEditor(topicName)
        keyValueCombobox.item = KafkaRegistryKeyValue.VALUE
        strategyCombobox.item = ConfluentRegistryStrategy.TOPIC_NAME
      }
      TopicSchemaViewType.TOPIC -> {
        subjectNameField.text = topicName
        strategyCombobox.item = ConfluentRegistryStrategy.CUSTOM
      }
    }
  }

  fun applyRegistryInfo(schemaFormat: KafkaRegistryFormat, schemaDefinition: String) {
    formatCombobox.selectedItem = schemaFormat
    if (schemaFormat == KafkaRegistryFormat.PROTOBUF) {
      textScrollPane.setText(schemaDefinition, KafkaRegistryUtil.protobufLanguage)
    }
    else {
      textScrollPane.setText(KafkaEditorUtils.tryFormatJson(schemaDefinition), JsonLanguage.INSTANCE)
    }
  }

  override fun createCenterPanel(): JComponent = panel

  override fun getDimensionServiceKey(): String = "io.confluent.kafka.core.ui.add.kafka.registry.dialog.bounds"

  private fun getSchemaName(): String = when (strategyCombobox.item) {
    ConfluentRegistryStrategy.TOPIC_NAME -> subjectNameField.text
    ConfluentRegistryStrategy.RECORD_NAME -> recordField.text
    ConfluentRegistryStrategy.TOPIC_RECORD_NAME -> subjectNameField.text
    ConfluentRegistryStrategy.CUSTOM -> subjectNameField.text
    else -> subjectNameField.text
  }

  private fun onChangeFormat() {
    val newDefault = KafkaRegistryTemplates.getDefaultIfNotConfigured(textScrollPane.text, getFormat())
    textScrollPane.setText(newDefault ?: textScrollPane.text,
                           if (formatCombobox.selectedItem != KafkaRegistryFormat.PROTOBUF) JsonLanguage.INSTANCE
                           else KafkaRegistryUtil.protobufLanguage)
    updateRecordFieldText()
  }

  private fun onChangeStrategy() {
    if (!isConfluentSchema) {
      subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.schema.name")
      subjectFieldVisible.set(true)
      return
    }
    updateParsedSchema()

    when (getStrategy()) {
      ConfluentRegistryStrategy.CUSTOM -> {
        subjectFieldVisible.set(true)
        subjectNameField.isEditable = true
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.custom.name")
        keyValueVisible.set(false)
        topicFieldVisible.set(false)
        recordFieldVisible.set(false)
      }
      ConfluentRegistryStrategy.TOPIC_NAME -> {
        subjectFieldVisible.set(true)
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        keyValueVisible.set(true)
        topicFieldVisible.set(true)
        recordFieldVisible.set(false)

        subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + keyValueCombobox.item?.name?.lowercase()
      }
      ConfluentRegistryStrategy.RECORD_NAME -> {
        subjectFieldVisible.set(false)
        keyValueVisible.set(false)
        topicFieldVisible.set(false)
        recordFieldVisible.set(true)

        updateRecordFieldText()
      }
      ConfluentRegistryStrategy.TOPIC_RECORD_NAME -> {
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
    if (strategy == ConfluentRegistryStrategy.RECORD_NAME) {
      val newRecordName = KafkaRegistryUtil.parseRecordName(cachedParsedSchema) ?: ""
      if (recordField.text != newRecordName) {
        recordField.text = newRecordName
      }
    }

    if (strategy == ConfluentRegistryStrategy.TOPIC_RECORD_NAME) {
      val newRecordName = KafkaRegistryUtil.parseRecordName(cachedParsedSchema) ?: ""
      subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + newRecordName.ifBlank { "<record>" }
    }
  }

  private fun getFormat() = formatCombobox.item
  private fun getStrategy(): ConfluentRegistryStrategy = strategyCombobox.item

  private fun updateParsedSchema() {
    val parsedSchemaResult = KafkaRegistryUtil.parseSchema(getFormat(), textScrollPane.text, dataManager, emptyList())

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
      val parsedSchema = cachedParsedSchema ?: return

      val createAsync = dataManager.createSchema(schemaName, parsedSchema)
      createAsync.onError {
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