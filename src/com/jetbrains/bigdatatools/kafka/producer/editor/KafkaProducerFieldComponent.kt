package com.jetbrains.bigdatatools.kafka.producer.editor

import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.readBytes
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.bigdatatools.core.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.core.settings.getValidationInfo
import com.jetbrains.bigdatatools.core.settings.revalidateComponent
import com.jetbrains.bigdatatools.core.settings.withValidator
import com.jetbrains.bigdatatools.core.ui.chooser.FileChooserUtil
import com.jetbrains.bigdatatools.core.ui.revalidateOnLinesChanged
import com.jetbrains.bigdatatools.core.util.executeNotOnEdt
import com.jetbrains.bigdatatools.core.util.invokeLater
import com.jetbrains.bigdatatools.core.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.generator.GenerateRandomData
import java.util.*

class KafkaProducerFieldComponent(private val producedEditor: KafkaProducerEditor, val isKey: Boolean) : Disposable {
  private var isInited: Boolean = false
  val project = producedEditor.project
  private val kafkaManager = producedEditor.kafkaManager

  private var schemaValidationError: Throwable? = null
  private var curIsJsonView: Boolean = !isKey

  val fieldTypeComboBox = KafkaEditorUtils.createFieldTypeComboBox(producedEditor.topicComboBox, kafkaManager, isKey) {
    if (!isInited)
      return@createFieldTypeComboBox

    updateVisibility()

    val newIsJsonView = it.item in jsonFieldTypes
    if (newIsJsonView != curIsJsonView) {
      if (curIsJsonView)
        updateFieldsText(it.item, jsonField.text)
      else
        updateFieldsText(it.item, textField.text)
    }
    curIsJsonView = newIsJsonView

    revalidateFields()
    producedEditor.mainComponent.revalidate()
  }

  val schemaComboBox = KafkaEditorUtils.createSchemaComboBox(this, kafkaManager,
                                                             producedEditor.topicComboBox, isKey)

  private val textField: EditorTextField by lazy {
    KafkaEditorUtils.createTextArea(project, language = PlainTextLanguage.INSTANCE).withValidator(this, ::validateValue).also {
      it.setDisposedWith(this@KafkaProducerFieldComponent)
      it.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          schemaValidationError = null
        }
      }, this)
      it.revalidateOnLinesChanged()
    }
  }

  private val jsonField: EditorTextField by lazy {
    KafkaEditorUtils.createTextArea(project).withValidator(this, ::validateValue).also {
      it.setDisposedWith(this@KafkaProducerFieldComponent)
      it.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          schemaValidationError = null
        }
      }, this)
      it.revalidateOnLinesChanged()
    }
  }

  override fun dispose() {}

  private fun revalidateFields() {
    textField.revalidateComponent()
    jsonField.revalidateComponent()
  }

  fun validateSchema(): Boolean {
    val oldValidationError = schemaValidationError?.toPresentableText()

    return try {
      val producerField = getProducerField()
      if (producerField.type == KafkaFieldType.SCHEMA_REGISTRY) {
        producerField.parsedSchema?.validate()
        producerField.getValueObj()
      }
      schemaValidationError = null
      true
    }
    catch (t: Throwable) {
      schemaValidationError = t
      false
    }
    finally {
      if (schemaValidationError?.toPresentableText() != oldValidationError)
        revalidateFields()
    }
  }

  @RequiresBackgroundThread
  fun getProducerField(): ConsumerProducerFieldConfig {
    val fieldType = fieldTypeComboBox.item
    val registryType = kafkaManager.registryType
    val schemaName = schemaComboBox.item?.schemaName ?: ""
    val schemaFormat = schemaComboBox.item?.schemaFormat ?: KafkaRegistryFormat.UNKNOWN
    val schema = KafkaRegistryUtil.loadSchema(schemaName, fieldType, kafkaManager)

    return ConsumerProducerFieldConfig(type = fieldType,
                                       valueText = getValueText(),
                                       isKey = isKey,
                                       topic = producedEditor.topicComboBox.item.name,
                                       registryType = registryType,
                                       schemaName = schemaName,
                                       schemaFormat = schemaFormat,
                                       parsedSchema = schema)
  }

  private lateinit var registryRows: RowsRange

  private lateinit var textRow: Row
  private lateinit var jsonRow: Row
  private lateinit var loadFileLinkRow: Row
  private lateinit var jsonCell: Cell<EditorTextField>

  private lateinit var generateDataAction: Cell<ActionButton>

  fun getValidationInfo() =
    textField.getValidationInfo() ?: jsonField.getValidationInfo() ?: schemaComboBox.getValidationInfo()

  fun createComponent(panel: Panel) {
    panel.apply {
      val title = if (isKey)
        KafkaMessagesBundle.message("consumer.producer.key.group")
      else
        KafkaMessagesBundle.message("consumer.producer.value.group")

      group(title, indent = false) {
        row(KafkaMessagesBundle.message("consumer.producer.format.type")) {
          cell(fieldTypeComboBox).resizableColumn().gap(RightGap.SMALL)
          generateDataAction = actionButton(createGenerateAction())
        }
        registryRows = rowsRange {
          row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
            cell(schemaComboBox).onChanged { schemaValidationError = null }.resizableColumn().gap(RightGap.SMALL)
            actionButton(DumbAwareAction.create(KafkaMessagesBundle.message("show.schema.info"), AllIcons.Actions.ToggleVisibility) {
              executeNotOnEdt {
                try {
                  val config = getProducerField()
                  val schema = config.parsedSchema ?: return@executeNotOnEdt
                  invokeLater {
                    KafkaSchemaInfoDialog.show(project = project, schemaType = schema.schemaType(),
                                               schemaDefinition = schema.canonicalString(),
                                               schemaName = config.schemaName)
                  }
                }
                catch (t: Throwable) {
                  RfsNotificationUtils.showExceptionMessage(project, t)
                }
              }
            })
          }
        }

        jsonRow = row {
          jsonCell = cell(jsonField).align(AlignX.FILL).resizableColumn().comment("")
        }
        textRow = row {
          cell(textField).align(AlignX.FILL).resizableColumn()
        }
        loadFileLinkRow = row {
          link(KafkaMessagesBundle.message("producer.config.link.upload.file")) {
            val vf = FileChooserUtil.selectSingleFile(project) ?: return@link
            executeNotOnEdt {
              val readBytes = vf.readBytes()
              invokeLater {
                textField.text = Base64.getEncoder().encodeToString(readBytes)
              }
            }
          }
        }.topGap(TopGap.NONE)
      }
    }
    isInited = true
    updateVisibility()
  }

  private fun updateFieldsText(type: KafkaFieldType, newText: String) = runWriteAction {
    if (type in jsonFieldTypes)
      jsonField.text = newText
    else
      textField.text = newText
  }

  @Suppress("UNUSED_PARAMETER")
  private fun validateValue(text: String) = if (schemaValidationError != null)
    schemaValidationError?.message ?: schemaValidationError?.toPresentableText()
  else
    validate(fieldTypeComboBox.item, getValueText())

  fun getValueText(): String = when (fieldTypeComboBox.item!!) {
    KafkaFieldType.JSON -> jsonField.text
    KafkaFieldType.STRING, KafkaFieldType.LONG, KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64 -> textField.text
    KafkaFieldType.NULL -> ""
    KafkaFieldType.SCHEMA_REGISTRY -> jsonField.text
  }

  private fun updateVisibility(): Unit = invokeLater {
    val fieldType = fieldTypeComboBox.item

    jsonRow.visible(fieldType in jsonFieldTypes)
    textRow.visible(fieldType in textFieldTypes)
    loadFileLinkRow.visible(fieldType == KafkaFieldType.BASE64)

    val isRegistryType = fieldType in KafkaFieldType.registryValues
    registryRows.visible(isRegistryType)

    invokeLater {
      generateDataAction.component.update()
    }

    updateJsonComment()
  }

  private fun validate(type: KafkaFieldType, value: String) = when (type) {
    KafkaFieldType.JSON -> try {
      JsonParser.parseString(value)
      null
    }
    catch (iae: Exception) {
      iae.cause?.message ?: iae.message
    }
    KafkaFieldType.STRING -> null
    KafkaFieldType.LONG -> if (value.toLongOrNull() != null) null else "'$value' is not a valid long value"
    KafkaFieldType.DOUBLE -> if (value.toDoubleOrNull() != null) null else "'$value' is not a valid double value"
    KafkaFieldType.FLOAT -> if (value.toFloatOrNull() != null) null else "'$value' is not a valid float value"
    KafkaFieldType.BASE64 -> {
      val decoder = Base64.getDecoder()
      try {
        decoder.decode(value)
        null
      }
      catch (iae: IllegalArgumentException) {
        iae.message
      }
    }
    KafkaFieldType.NULL -> null // Any value match null type
    KafkaFieldType.SCHEMA_REGISTRY -> try {
      JsonParser.parseString(value)
      executeNotOnEdt {
        validateSchema()
      }
      null
    }
    catch (iae: Exception) {
      iae.cause?.message ?: iae.message
    }
  }

  fun applyConfig(config: StorageProducerConfig) {
    fieldTypeComboBox.item = if (isKey) config.getKeyType() else config.getValueType()

    val text = if (isKey) config.key else config.value
    when (config.getKeyType()) {
      KafkaFieldType.JSON -> jsonField.text = text
      KafkaFieldType.STRING, KafkaFieldType.LONG, KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64 -> textField.text = text
      KafkaFieldType.NULL -> Unit
      KafkaFieldType.SCHEMA_REGISTRY -> {
        jsonField.text = text
        schemaComboBox.item = if (isKey)
          RegistrySchemaInEditor(config.keySubject, config.getKeyFormat())
        else
          RegistrySchemaInEditor(config.valueSubject, config.getValueFormat())
      }
    }
  }

  private fun updateJsonComment() {
    jsonCell.comment?.text = when (fieldTypeComboBox.item) {
      null, KafkaFieldType.STRING, KafkaFieldType.JSON, KafkaFieldType.LONG, KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64, KafkaFieldType.NULL -> ""
      KafkaFieldType.SCHEMA_REGISTRY -> KafkaMessagesBundle.message(
        "producer.json.value.comment")
    }
  }

  private fun createGenerateAction() = object : DumbAwareAction(KafkaMessagesBundle.message("generate.random.data"), null,
                                                                AllIcons.Diff.MagicResolveToolbar) {
    override fun actionPerformed(e: AnActionEvent) {
      executeNotOnEdt {
        val config = getProducerField()
        invokeLater {
          updateFieldsText(config.type, GenerateRandomData.generate(config.type, config.parsedSchema))
        }
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      when (fieldTypeComboBox.item) {
        KafkaFieldType.STRING,
        KafkaFieldType.LONG,
        KafkaFieldType.DOUBLE,
        KafkaFieldType.FLOAT,
        KafkaFieldType.BASE64,
        KafkaFieldType.JSON,
        KafkaFieldType.SCHEMA_REGISTRY,
        -> {
          e.presentation.isEnabledAndVisible = true
          e.presentation.text = KafkaMessagesBundle.message("generate.random.data")
        }
        null, KafkaFieldType.NULL -> e.presentation.isEnabledAndVisible = false
      }
    }
  }

  companion object {
    private val jsonFieldTypes = setOf(KafkaFieldType.JSON) + KafkaFieldType.registryValues
    private val textFieldTypes = setOf(KafkaFieldType.STRING, KafkaFieldType.LONG, KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT,
                                       KafkaFieldType.BASE64)
  }
}