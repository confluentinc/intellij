package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.kafka.common.editor.FieldViewerType
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.ui.EmptyCell
import com.jetbrains.bigdatatools.ui.MigPanel
import com.jetbrains.bigdatatools.util.SizeUtils
import com.jetbrains.bigdatatools.util.TimeUtils
import net.miginfocom.layout.CC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.ItemEvent
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent

class ConsumerRecordDetails(project: Project, parentDisposable: Disposable) {

  inner class ConsumerEditorCustomization : EditorCustomization {
    override fun customize(editor: EditorEx) {

      editor.scrollPane.layout = object : JBScrollPane.Layout() {
        override fun preferredLayoutSize(parent: Container?): Dimension {
          val superSize = super.preferredLayoutSize(parent)

          return Dimension(superSize.width,
                           superSize.height + (if (horizontalScrollBar?.isVisible == true) horizontalScrollBar.height * 3 else 0))
        }
      }
    }
  }

  inner class AdjustableScrollPanel(view: Component) : JBScrollPane(view) {

    init {
      // In the other case the borders will be removed when the component placed in the Editor.
      putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL)
      border = BorderFactory.createLineBorder(JBColor.border())
    }

    override fun getPreferredSize(): Dimension {
      val superSize = super.getPreferredSize()
      return Dimension(superSize.width,
                       superSize.height + (if (horizontalScrollBar?.isVisible == true) horizontalScrollBar.height * 2 else 0))
    }
  }

  private val topicField = JTextField(10).apply { isEditable = false }

  private val keyViewerType = ComboBox(FieldViewerType.values()).apply {
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }

  private val keyFieldText = JTextArea().apply {
    isEditable = false
    border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
  }

  private val keyFieldTextScroll = AdjustableScrollPanel(keyFieldText)
  private val keyFieldJson: EditorTextField

  private val valueViewerType = ComboBox(FieldViewerType.values()).apply {
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }

  private val valueFieldText = JTextArea().apply {
    isEditable = false
    border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
  }

  private val valueFieldTextScroll = AdjustableScrollPanel(valueFieldText)
  private val valueFieldJson: EditorTextField

  private val headers = PropertiesTable(emptyList())
  private val partition = JTextField(10).apply { isEditable = false }
  private val offset = JTextField(10).apply { isEditable = false }
  private val timestamp = JTextField(10).apply {
    isEditable = false
    (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
  }
  private val timestampType = JTextField(10).apply { isEditable = false }
  private val keySize = JTextField(10).apply { isEditable = false }
  private val valueSize = JTextField(10).apply { isEditable = false }

  var keyType = FieldType.JSON
    set(value) {
      if (field == value) {
        return
      }
      field = value
      updateFieldEditor(keyFieldText, keyFieldJson, field, keyViewerType.item)
      record = record
    }

  var valueType = FieldType.JSON
    set(value) {
      if (field == value) {
        return
      }
      field = value
      updateFieldEditor(valueFieldText, valueFieldJson, field, valueViewerType.item)
      record = record
    }

  init {
    keyFieldJson = KafkaEditorUtils.createJsonTextArea(project, listOf(ConsumerEditorCustomization())).apply {
      document.setReadOnly(true)
      setDisposedWith(parentDisposable)
    }

    valueFieldJson = KafkaEditorUtils.createJsonTextArea(project, listOf(ConsumerEditorCustomization())).apply {
      document.setReadOnly(true)
      setDisposedWith(parentDisposable)
    }

    keyViewerType.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) {
        updateFieldEditor(keyFieldText, keyFieldJson, keyType, keyViewerType.item)
        updateField(keyFieldText, keyFieldJson, keyViewerType.item, keyType, record?.key())
        component.revalidate()
      }
    }

    valueViewerType.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) {
        updateFieldEditor(valueFieldText, valueFieldJson, valueType, valueViewerType.item)
        updateField(valueFieldText, valueFieldJson, valueViewerType.item, valueType, record?.value())
        component.revalidate()
      }
    }
  }

  private fun isJsonViewer(fieldType: FieldType,
                           fieldViewerType: FieldViewerType) = fieldType == FieldType.JSON && fieldViewerType == FieldViewerType.AUTO || fieldViewerType == FieldViewerType.JSON

  private fun updateFieldEditor(fieldText: JTextComponent,
                                fieldJson: EditorTextField,
                                fieldType: FieldType,
                                fieldViewerType: FieldViewerType) {
    val isJson = isJsonViewer(fieldType, fieldViewerType)

    if (isJson) {
      fieldJson.editor?.settings?.isFoldingOutlineShown = true
    }

    val fieldTextScroll = if (fieldText == valueFieldText) valueFieldTextScroll else keyFieldTextScroll
    fieldTextScroll.isVisible = !isJson
    fieldJson.isVisible = isJson
  }

  private fun updateField(field: EditorTextField, value: String) {
    field.document.setReadOnly(false)
    field.text = value
    field.document.setReadOnly(true)
  }

  private fun updateField(fieldText: JTextComponent,
                          fieldJson: EditorTextField,
                          fieldViewerType: FieldViewerType,
                          fieldType: FieldType,
                          value: Any?) {

    val isJson = isJsonViewer(fieldType, fieldViewerType)

    if (value == null) {
      if (isJson)
        updateField(fieldJson, "")
      else
        fieldText.text = ""
      return
    }

    val presentingValue = when {
      fieldViewerType == FieldViewerType.JSON -> {
        try {
          val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
          gson.toJson(JsonParser.parseString(value.toString()))
        }
        catch (e: Exception) {
          value.toString()
        }
      }
      fieldViewerType == FieldViewerType.DECODED_BASE64 && value is ByteArray -> {
        try {
          Base64.getEncoder().withoutPadding().encodeToString(value)
        }
        catch (e: Exception) {
          value.toString()
        }
      }
      fieldViewerType == FieldViewerType.AUTO -> KafkaEditorUtils.getValueAsString(fieldType, value)
      else -> value.toString()
    }
    if (isJson) {
      updateField(fieldJson, presentingValue)
    }
    else {
      fieldText.text = presentingValue
    }
    fieldText.caretPosition = 0
  }

  var record: ConsumerRecord<Any, Any>? = null
    set(value) {
      field = value

      if (value == null) {
        updateField(keyFieldJson, "")
        updateField(valueFieldJson, "")

        topicField.text = ""

        partition.text = ""
        offset.text = ""
        timestamp.text = ""
        timestampType.text = ""
        keySize.text = ""
        valueSize.text = ""

        headers.clear()
      }
      else {
        topicField.text = value.topic()

        updateField(keyFieldText, keyFieldJson, keyViewerType.item, keyType, value.key())
        updateField(valueFieldText, valueFieldJson, valueViewerType.item, valueType, value.value())

        partition.text = value.partition().toString()
        offset.text = value.offset().toString()
        timestamp.text = TimeUtils.unixTimeToString(value.timestamp())
        timestampType.text = value.timestampType().toString()
        keySize.text = SizeUtils.toString(if (value.serializedKeySize() == -1) 0 else value.serializedKeySize())
        valueSize.text = SizeUtils.toString(if (value.serializedValueSize() == -1) 0 else value.serializedValueSize())

        val headerProperties = value.headers().map { Property(it.key(), String(it.value(), StandardCharsets.UTF_8)) }
        headers.properties = headerProperties.toMutableList()
      }

      // Key and value Fields could contain multiline JSON
      component.revalidate()
    }

  val component = MigPanel(UiUtil.insets10FillXHidemode3).apply {
    row(KafkaMessagesBundle.message("consumer.record.topic"), topicField)
    add(JLabel(KafkaMessagesBundle.message("consumer.record.key")))
    add(keyViewerType, CC().pushX().alignX("right").wrap())
    block(keyFieldJson)
    block(keyFieldTextScroll)

    add(JLabel(KafkaMessagesBundle.message("consumer.record.value")))
    add(valueViewerType, CC().pushX().alignX("right").wrap())
    block(valueFieldJson)
    block(valueFieldTextScroll)

    row(KafkaMessagesBundle.message("consumer.record.partition"), partition)
    row(KafkaMessagesBundle.message("consumer.record.offset"), offset)
    row(KafkaMessagesBundle.message("consumer.record.timestamp"), timestamp)
    row(EmptyCell(), timestampType)
    row(KafkaMessagesBundle.message("consumer.record.keysize"), keySize)
    row(KafkaMessagesBundle.message("consumer.record.valuesize"), valueSize)

    add(JLabel(KafkaMessagesBundle.message("consumer.record.headers")), UiUtil.wrap)
    block(JBScrollPane(headers.table))
  }
}