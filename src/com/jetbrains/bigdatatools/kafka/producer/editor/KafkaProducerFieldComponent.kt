package com.jetbrains.bigdatatools.kafka.producer.editor

import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.revalidateComponent
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.SimpleDumbAwareAction
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.common.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.generator.GenerateRandomData
import java.util.*

class KafkaProducerFieldComponent(private val producedEditor: KafkaProducerEditor, val isKey: Boolean) : Disposable {
  val project = producedEditor.project
  private val kafkaManager = producedEditor.kafkaManager

  private var schemaValidationError: Throwable? = null
  val schemaComboBox = KafkaEditorUtils.createSchemaComboBox(this, kafkaManager,
                                                             producedEditor.topicComboBox, isKey)


  val textField = JBTextField().apply { emptyText.text = "Optional" }.withValidator(this, ::validateValue)

  val jsonField: EditorTextField by lazy {
    KafkaEditorUtils.createJsonTextArea(project).withValidator(this, ::validateValue).also {
      it.setDisposedWith(this@KafkaProducerFieldComponent)
      it.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          schemaValidationError = null
        }
      }, this)
    }
  }

  private val supportedFieldTypes = if (kafkaManager.registryType != KafkaRegistryType.NONE) FieldType.allValues else FieldType.defaultValues

  val fieldTypeComboBox = ComboBox(supportedFieldTypes.toTypedArray()).apply<ComboBox<FieldType>> {
    renderer = CustomListCellRenderer<FieldType> { it.title }
    selectedItem = FieldType.STRING
    addActionListener {
      updateVisibility()

      updateFieldsText(item, "")
      jsonField.revalidateComponent()
      textField.revalidateComponent()
      producedEditor.mainComponent.revalidate()
    }
  }

  override fun dispose() {}

  private fun revalidateFields() {
    textField.revalidateComponent()
    jsonField.revalidateComponent()
  }

  fun validateSchema(): Boolean {
    schemaValidationError = null
    return try {
      getProducerField()
      true
    }
    catch (t: Throwable) {
      schemaValidationError = t
      false
    }
    finally {
      revalidateFields()
    }
  }

  fun getProducerField(): ConsumerProducerFieldConfig {
    val fieldType = fieldTypeComboBox.item
    val registryType = kafkaManager.registryType
    val schemaName = schemaComboBox.item?.schemaName ?: ""
    val schema = KafkaRegistryUtil.loadSchema(registryType, schemaName, fieldType, kafkaManager)

    return ConsumerProducerFieldConfig(type = fieldType,
                                       valueText = getValueText(),
                                       isKey = isKey,
                                       topic = producedEditor.topicComboBox.item.name,
                                       registryType = registryType,
                                       schemaName = schemaName,
                                       parsedSchema = schema)
  }

  private lateinit var registryRows: RowsRange

  private lateinit var textRow: Row
  private lateinit var jsonRow: Row
  private lateinit var jsonCell: Cell<EditorTextField>

  private lateinit var generateDataAction: Cell<ActionButton>

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
            actionButton(SimpleDumbAwareAction(KafkaMessagesBundle.message("show.schema.info"),
                                               AllIcons.Actions.ToggleVisibility) {
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
          cell(textField).align(AlignX.FILL).resizableColumn().onChanged {
            schemaValidationError = null
          }
        }
      }
    }
    updateVisibility()
  }


  private fun updateFieldsText(type: FieldType, newText: String) {
    if (type in FieldType.registryValues || type == FieldType.JSON)
      jsonField.text = newText
    else if (type != FieldType.NULL)
      textField.text = newText
    else return
  }

  @Suppress("UNUSED_PARAMETER")
  private fun validateValue(text: String): String? {
    if (schemaValidationError != null)
      return schemaValidationError?.toPresentableText()
    return validate(fieldTypeComboBox.item, getValueText())
  }

  fun getValueText(): String = when (fieldTypeComboBox.item!!) {
    FieldType.JSON -> jsonField.text
    FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> textField.text
    FieldType.NULL -> ""
    FieldType.AVRO_REGISTRY, FieldType.JSON_REGISTRY, FieldType.PROTOBUF_REGISTRY -> jsonField.text
  }


  private fun updateVisibility() {
    val fieldType = fieldTypeComboBox.item

    jsonRow.visible(fieldType in jsonFieldTypes)
    textRow.visible(fieldType in textFieldTypes)

    val isRegistryType = fieldType in FieldType.registryValues
    registryRows.visible(isRegistryType)

    generateDataAction.component.update()

    updateJsonComment()
  }


  private fun validate(type: FieldType, value: String) = when (type) {
    FieldType.JSON -> try {
      JsonParser.parseString(value)
      null
    }
    catch (iae: Exception) {
      iae.cause?.message ?: iae.message
    }
    FieldType.STRING -> null
    FieldType.LONG -> if (value.toLongOrNull() != null) null else "'$value' is not a valid long value"
    FieldType.DOUBLE -> if (value.toDoubleOrNull() != null) null else "'$value' is not a valid double value"
    FieldType.FLOAT -> if (value.toFloatOrNull() != null) null else "'$value' is not a valid float value"
    FieldType.BASE64 -> {
      val decoder = Base64.getDecoder()
      try {
        decoder.decode(value)
        null
      }
      catch (iae: IllegalArgumentException) {
        iae.message
      }
    }
    FieldType.NULL -> null // Any value match null type
    FieldType.AVRO_REGISTRY, FieldType.PROTOBUF_REGISTRY, FieldType.JSON_REGISTRY -> try {
      JsonParser.parseString(value)
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
      FieldType.JSON -> jsonField.text = text
      FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> textField.text = text
      FieldType.NULL -> Unit
      FieldType.AVRO_REGISTRY, FieldType.JSON_REGISTRY, FieldType.PROTOBUF_REGISTRY -> {
        jsonField.text = text
        schemaComboBox.item = if (isKey)
          RegistrySchemaInEditor(config.keySubject, config.keyRegistry)
        else
          RegistrySchemaInEditor(config.valueSubject, config.valueRegistry)
      }
    }
  }

  private fun updateJsonComment() {
    jsonCell.comment?.text = when (fieldTypeComboBox.item) {
      null, FieldType.STRING, FieldType.JSON, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64, FieldType.NULL -> ""
      FieldType.JSON_REGISTRY, FieldType.PROTOBUF_REGISTRY, FieldType.AVRO_REGISTRY -> KafkaMessagesBundle.message(
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

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      when (fieldTypeComboBox.item) {
        FieldType.STRING,
        FieldType.LONG,
        FieldType.DOUBLE,
        FieldType.FLOAT,
        FieldType.BASE64,
        FieldType.AVRO_REGISTRY -> {
          e.presentation.isEnabledAndVisible = true
          e.presentation.text = KafkaMessagesBundle.message("generate.random.data")
        }
        FieldType.PROTOBUF_REGISTRY, FieldType.JSON, FieldType.JSON_REGISTRY -> {
          e.presentation.isVisible = true
          e.presentation.isEnabled = false
          @Suppress("DialogTitleCapitalization")
          e.presentation.text = KafkaMessagesBundle.message("generate.random.data.not.supported")
        }
        null, FieldType.NULL -> e.presentation.isVisible = false
      }
    }
  }


  companion object {
    private val jsonFieldTypes = setOf(FieldType.JSON) + FieldType.registryValues
    private val textFieldTypes = setOf(FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64)
  }
}