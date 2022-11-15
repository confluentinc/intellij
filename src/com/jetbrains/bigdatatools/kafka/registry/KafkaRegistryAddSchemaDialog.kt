package com.jetbrains.bigdatatools.kafka.registry

import com.intellij.json.JsonLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization
import com.intellij.ui.components.JBLabel
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.MigBlock
import com.jetbrains.bigdatatools.common.ui.MigPanel
import com.jetbrains.bigdatatools.common.ui.doOnChange
import com.jetbrains.bigdatatools.common.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.math.max
import kotlin.math.min


/**
 * TODO: @nikita.pavlenko
 * - add more pretty validation message place
 * - rewrite validation logic if reqired, seems that parse new cache values perform after validation
 * - disable ok if schema is not parsed
 * - for protobuf provide another highlihter or PlainText if not exists
 * - check conductor ui, maybe we need change comboboxes to redioboxes and remove or rewrite some fields
 */
class KafkaRegistryAddSchemaDialog(project: Project,
                                   val dataManager: KafkaDataManager) : DialogWrapper(project, true, IdeModalityType.PROJECT) {
  private val formatCombobox = ComboBox(KafkaRegistryFormat.values()).apply {
    isSwingPopup = false
    renderer = CustomListCellRenderer<KafkaRegistryFormat> { it.presentable }
    selectedItem = KafkaRegistryFormat.values().first()
    addItemListener {
      onChangeFormat()
    }
  }

  private val strategyCombobox = ComboBox(KafkaRegistryStrategy.values()).apply {
    isSwingPopup = false
    renderer = CustomListCellRenderer<KafkaRegistryStrategy> { it.presentable }
    selectedItem = KafkaRegistryFormat.values().first()
    addItemListener {
      onChangeStrategy()
    }
  }

  private val keyValueCombobox = ComboBox(KafkaRegistryKeyValue.values()).apply {
    isSwingPopup = false
    renderer = CustomListCellRenderer<KafkaRegistryKeyValue> { it.presentable }
    selectedItem = KafkaRegistryFormat.values().first()
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

  private var recordBlock: MigBlock? = null
  private var topicBlock: MigBlock? = null
  private var topicKeyBlock: MigBlock? = null

  private val subjectNameField = JTextField("")
  private val subjectNameLabel = JBLabel("")

  private var cachedParsedSchema: ParsedSchema? = null
  private var cachedNotificationError: Throwable? = null

  private val jsonTextArea = EditorTextFieldProvider.getInstance().getEditorField(JsonLanguage.INSTANCE, project, listOf(
    EditorCustomization {
      it.settings.apply {
        isLineNumbersShown = false
        isLineMarkerAreaShown = false
        isFoldingOutlineShown = false
        isRightMarginShown = false
        isAdditionalPageAtBottom = false
        isShowIntentionBulb = false
      }
    }, MonospaceEditorCustomization.getInstance())).apply {
    setDisposedWith(disposable)
    autoscrolls = false
    setCaretPosition(0)
  }.withValidator(disposable) {
    cachedNotificationError?.toPresentableText()
  }.also {
    it.document.doOnChange {
      onChangeStrategy()
    }
  }

  private val panel = MigPanel().apply {
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.format"), formatCombobox)
    row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.strategy"), strategyCombobox)
    topicBlock = MigBlock(this).also {
      topicKeyBlock = MigBlock(this).apply {
        row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.key.value"), keyValueCombobox)
      }

      it.row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.topic"), topicField)
    }
    recordBlock = MigBlock(this).also {
      it.row(KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.record"), recordField)
    }

    row(subjectNameLabel, subjectNameField)
    block(JPanel(BorderLayout()).apply {
      add(jsonTextArea, BorderLayout.CENTER)
      preferredSize = Dimension(min(800, max(350, preferredSize.width)),
                                min(600, max(400, preferredSize.height)))
    }
    )
  }

  init {
    init()
    title = KafkaMessagesBundle.message("registry.add.schema.dialog.title")
    onChangeFormat()
  }

  override fun createCenterPanel(): JComponent = panel

  override fun getDimensionServiceKey() = "com.jetbrains.bigdatatools.common.ui.add.kafka.registry.dialog.bounds"

  fun getSchemaName(): String = subjectNameField.text
  fun getParsedSchema(): ParsedSchema = KafkaRegistryUtil.parseSchema(getFormat(), jsonTextArea.text)

  override fun isOKActionEnabled(): Boolean {
    return cachedNotificationError == null
  }

  private fun onChangeFormat() {
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
        topicBlock?.isVisible = false
        topicKeyBlock?.isVisible = false
        recordBlock?.isVisible = false
      }
      KafkaRegistryStrategy.TOPIC_NAME -> {
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        topicBlock?.isVisible = true
        topicKeyBlock?.isVisible = true
        recordBlock?.isVisible = false

        subjectNameField.text = topicField.item?.name?.ifBlank { "<topic>" } + "-" + keyValueCombobox.item.name.lowercase()
      }
      KafkaRegistryStrategy.RECORD_NAME -> {
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        topicBlock?.isVisible = false
        topicKeyBlock?.isVisible = false
        recordBlock?.isVisible = true

        updateRecordFieldText()
        subjectNameField.text = recordField.text
      }
      KafkaRegistryStrategy.TOPIC_RECORD_NAME -> {
        subjectNameField.isEditable = false
        subjectNameLabel.text = KafkaMessagesBundle.message("schema.registry.add.schema.dialog.field.computed.name")
        topicBlock?.isVisible = true
        recordBlock?.isVisible = true
        topicKeyBlock?.isVisible = false

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
    if (recordField.text != newRecordName)
      recordField.text = newRecordName
  }

  private fun getFormat() = (formatCombobox.selectedItem as KafkaRegistryFormat).name
  private fun getStrategy() = (strategyCombobox.selectedItem as KafkaRegistryStrategy)

  private fun updateParsedSchema() = try {
    cachedParsedSchema = getParsedSchema()
    cachedNotificationError = null
  }
  catch (t: Throwable) {
    cachedParsedSchema = null
    cachedNotificationError = t
  }
}