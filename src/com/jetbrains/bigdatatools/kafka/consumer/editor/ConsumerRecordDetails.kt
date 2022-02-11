package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.ui.DarculaTextAreaBorder
import com.jetbrains.bigdatatools.ui.EmptyCell
import com.jetbrains.bigdatatools.ui.MigPanel
import com.jetbrains.bigdatatools.util.SizeUtils
import com.jetbrains.bigdatatools.util.TimeUtils
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.nio.charset.StandardCharsets
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTextField

class ConsumerRecordDetails(project: Project, parentDisposable: Disposable) {
  private val topicField = JTextField(10)
  private val keyField = createTextEditor(project, parentDisposable)
  private val valueField = createTextEditor(project, parentDisposable)
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

  private fun setKey(value: String) {
    keyField.editor?.document?.setReadOnly(false)
    keyField.text = value
    keyField.editor?.document?.setReadOnly(true)
  }

  private fun setValue(value: String) {
    valueField.editor?.document?.setReadOnly(false)
    valueField.text = value
    valueField.editor?.document?.setReadOnly(true)
  }

  var record: ConsumerRecord<Any, Any>? = null
    set(value) {
      field = value

      if (value == null) {
        setKey("")
        setValue("")

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
        setKey(KafkaEditorUtils.getValueAsString(keyType, value.key()))
        setValue(KafkaEditorUtils.getValueAsString(valueType, value.value()))

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

  val component = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {
    row(KafkaMessagesBundle.message("consumer.record.topic"), topicField)
    row(KafkaMessagesBundle.message("consumer.record.key"))
    block(keyField)
    row(KafkaMessagesBundle.message("consumer.record.value"))
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

  private fun createTextEditor(project: Project, parentDisposable: Disposable) = EditorTextFieldProvider.getInstance().getEditorField(
    JsonLanguage.INSTANCE, project,
    listOf(
      EditorCustomization {
        it.settings.apply {
          isLineNumbersShown = false
          isLineMarkerAreaShown = false
          isFoldingOutlineShown = false
          isRightMarginShown = false
          additionalLinesCount = 0
          additionalColumnsCount = 0
          isAdditionalPageAtBottom = false
          isShowIntentionBulb = false
        }
      },
      MonospaceEditorCustomization.getInstance())).apply {

    border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
    background = UIUtil.getTextFieldBackground()

    autoscrolls = false
    document.setReadOnly(true)
    setCaretPosition(0)

    setDisposedWith(parentDisposable)
  }
}