package com.jetbrains.bigdatatools.kafka.producer.editor

import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.revalidateComponent
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.SimpleDumbAwareAction
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
import java.util.*

class KafkaProducerFieldComponent(private val producedEditor: KafkaProducerEditor, val isKey: Boolean) : Disposable {
  val project = producedEditor.project
  private val kafkaManager = producedEditor.kafkaManager

  private var schemaValidationError: Throwable? = null
  val schemaComboBox = KafkaEditorUtils.createSchemaComboBox(this, kafkaManager)


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
      val config = getProducerField()
      KafkaRegistryUtil.loadSchema(config, kafkaManager)
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

  fun getProducerField() = ConsumerProducerFieldConfig(type = fieldTypeComboBox.item,
                                                       valueText = getValueText(),
                                                       isKey = isKey,
                                                       topic = producedEditor.topicComboBox.item.name,

                                                       registryType = kafkaManager.registryType,
                                                       rawSchemaName = schemaComboBox.item?.schemaName ?: "")

  private lateinit var registryRows: RowsRange

  private lateinit var textRow: Row
  private lateinit var jsonRow: Row
  private lateinit var jsonCell: Cell<EditorTextField>

  fun createComponent(panel: Panel) {
    panel.apply {
      val title = if (isKey)
        KafkaMessagesBundle.message("consumer.producer.key.group")
      else
        KafkaMessagesBundle.message("consumer.producer.value.group")

      group(title, indent = false) {
        row(KafkaMessagesBundle.message("consumer.producer.format.type")) {
          cell(fieldTypeComboBox).align(Align.FILL)
        }
        registryRows = rowsRange {
          row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
            cell(schemaComboBox).onChanged { schemaValidationError = null }.resizableColumn().gap(RightGap.SMALL)
            actionButton(SimpleDumbAwareAction(KafkaMessagesBundle.message("show.schema.info"),
                                               AllIcons.Actions.ToggleVisibility) {
              try {
                val config = getProducerField()
                val schema = KafkaRegistryUtil.loadSchema(config, kafkaManager) ?: return@SimpleDumbAwareAction
                KafkaSchemaInfoDialog.show(project = project, schemaType = schema.schemaType(), schemaDefinition = schema.canonicalString(),
                                           schemaName = config.schemaName)
              }
              catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
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

  @Suppress("UNUSED_PARAMETER")
  private fun validateValue(text: String): String? {
    if (schemaValidationError != null) return schemaValidationError?.toPresentableText()
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

  companion object {
    private val jsonFieldTypes = setOf(FieldType.JSON) + FieldType.registryValues
    private val textFieldTypes = setOf(FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64)
  }
}