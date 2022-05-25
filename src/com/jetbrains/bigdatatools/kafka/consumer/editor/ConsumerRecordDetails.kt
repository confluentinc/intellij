package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBScrollPane
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
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTextField

class ConsumerRecordDetails(project: Project, parentDisposable: Disposable) {

  private val topicField = JTextField(10)

  private val keyViewerType = ComboBox(FieldViewerType.values()).apply {
    border = BorderFactory.createEmptyBorder()
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }

  private val keyField = KafkaEditorUtils.createJsonTextArea(project).apply {
    document.setReadOnly(true)
    setDisposedWith(parentDisposable)
  }

  private val valueViewerType = ComboBox(FieldViewerType.values()).apply {
    border = BorderFactory.createEmptyBorder()
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }

  private val valueField = KafkaEditorUtils.createJsonTextArea(project).apply {
    document.setReadOnly(true)
    setDisposedWith(parentDisposable)
  }

  init {
    keyViewerType.addItemListener {
      updateField(keyField, keyViewerType, keyType, record?.key())
      component.revalidate()
    }

    valueViewerType.addItemListener {
      updateField(valueField, valueViewerType, valueType, record?.value())
      component.revalidate()
    }
  }

  private val headers = PropertiesTable(emptyList())
  private val partition = JTextField(10)
  private val offset = JTextField(10)
  private val timestamp = JTextField(10)
  private val timestampType = JTextField(10)
  private val keySize = JTextField(10)
  private val valueSize = JTextField(10)

  var keyType = FieldType.STRING
    set(value) {
      if (field == value) {
        return
      }
      field = value
      keyField.editor?.settings?.isFoldingOutlineShown = (field == FieldType.JSON)
      record = record
    }

  var valueType = FieldType.STRING
    set(value) {
      if (field == value) {
        return
      }
      field = value
      valueField.editor?.settings?.isFoldingOutlineShown = (field == FieldType.JSON)
      record = record
    }

  private fun updateField(field: EditorTextField, value: String) {
    field.document.setReadOnly(false)
    field.text = value
    field.document.setReadOnly(true)
  }

  private fun updateField(field: EditorTextField, viewerType: ComboBox<FieldViewerType>, valueType: FieldType, value: Any?) {

    if (value == null) {
      updateField(field, "")
      return
    }

    val presentingValue = when {
      viewerType.item == FieldViewerType.JSON -> {
        try {
          val gson = GsonBuilder().setPrettyPrinting().create()
          gson.toJson(JsonParser.parseString(value.toString()))
        }
        catch (e: Exception) {
          value.toString()
        }
      }
      viewerType.item == FieldViewerType.DECODED_BASE64 && value is ByteArray -> {
        try {
          Base64.getEncoder().withoutPadding().encodeToString(value)
        }
        catch (e: Exception) {
          value.toString()
        }
      }
      viewerType.item == FieldViewerType.AUTO -> KafkaEditorUtils.getValueAsString(valueType, value)
      else -> value.toString()
    }

    updateField(field, presentingValue)
  }

  var record: ConsumerRecord<Any, Any>? = null
    set(value) {
      field = value

      if (value == null) {
        updateField(keyField, "")
        updateField(valueField, "")

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

        updateField(valueField, valueViewerType, valueType, value.value())
        updateField(keyField, keyViewerType, keyType, value.value())

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
    block(keyField)

    add(JLabel(KafkaMessagesBundle.message("consumer.record.value")))
    add(valueViewerType, CC().pushX().alignX("right").wrap())
    block(valueField)

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