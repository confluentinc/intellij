package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.writeBytes
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.scale.JBUIScale
import com.jetbrains.bigdatatools.core.rfs.driver.metainfo.components.SelectableLabel
import com.jetbrains.bigdatatools.core.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.core.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.core.ui.chooser.FileChooserUtil
import com.jetbrains.bigdatatools.core.util.SizeUtils
import com.jetbrains.bigdatatools.core.util.TimeUtils
import com.jetbrains.bigdatatools.kafka.common.editor.FieldViewerType
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.Container
import java.awt.Dimension
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import kotlin.math.min

class KafkaRecordDetails(project: Project, parentDisposable: Disposable) {
  private val topicField = SelectableLabel("")

  private lateinit var keyLoadFileLinkRow: Row
  private lateinit var valueLoadFileLinkRow: Row
  private val keyViewerType = ComboBox(FieldViewerType.values()).apply {
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }

  private val keyFieldText = JTextArea().apply {
    isEditable = false
    border = ComponentColoredBorder(3, 5, 3, 5)
  }

  private val keyFieldTextScroll = AdjustableScrollPanel(keyFieldText).apply {
  }
  private val keyFieldJson: EditorTextField

  private val valueViewerType = ComboBox(FieldViewerType.values()).apply {
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }

  private val valueFieldText = JTextArea().apply {
    isEditable = false
    border = ComponentColoredBorder(3, 5, 3, 5)
  }

  private val valueFieldTextScroll = AdjustableScrollPanel(valueFieldText)
  private val valueFieldJson: EditorTextField

  private val headers = PropertiesTable(emptyList(), isEditable = false)
  private val partition = SelectableLabel("")
  private val offset = SelectableLabel("")

  private val timestamp = SelectableLabel("")

  private val keySize = SelectableLabel("")
  private val valueSize = SelectableLabel("")

  private val keyTypeLabel = SelectableLabel("")
  private val valueTypeLabel = SelectableLabel("")

  private var keyType = KafkaFieldType.JSON
  private var valueType = KafkaFieldType.JSON

  init {
    keyFieldJson = KafkaEditorUtils.createTextArea(project, additionalCustomization = listOf(ConsumerEditorCustomization())).apply {
      document.setReadOnly(true)
      setDisposedWith(parentDisposable)
    }

    valueFieldJson = KafkaEditorUtils.createTextArea(project, additionalCustomization = listOf(ConsumerEditorCustomization())).apply {
      document.setReadOnly(true)
      setDisposedWith(parentDisposable)
    }

    keyViewerType.addActionListener {
      updateViewerVisible(keyViewerType, keyType, keyFieldTextScroll, keyFieldJson, keyLoadFileLinkRow)
      component.revalidate()
    }

    valueViewerType.addActionListener {
      updateViewerVisible(valueViewerType, valueType, valueFieldTextScroll, valueFieldJson, valueLoadFileLinkRow)
      component.revalidate()
    }


  }

  val component = JBScrollPane(panel {
    row(KafkaMessagesBundle.message("consumer.record.key")) {
      cell(keyViewerType).align(AlignX.RIGHT)
    }
    keyLoadFileLinkRow = row {
      link(KafkaMessagesBundle.message("producer.config.link.load.file")) {
        loadBinaryFile(project, "key", keyFieldText)
      }
    }

    row {
      cell(keyFieldJson).resizableColumn().align(AlignX.FILL)
      cell(keyFieldTextScroll).resizableColumn().align(AlignX.FILL)
    }.bottomGap(BottomGap.SMALL)

    row(KafkaMessagesBundle.message("consumer.record.value")) {
      cell(valueViewerType).align(AlignX.RIGHT)
    }
    valueLoadFileLinkRow = row {
      link(KafkaMessagesBundle.message("producer.config.link.load.file")) {
        loadBinaryFile(project, "value", valueFieldText)
      }
    }
    row {
      cell(valueFieldJson).resizableColumn().align(AlignX.FILL)
      cell(valueFieldTextScroll).resizableColumn().align(AlignX.FILL)
    }.bottomGap(BottomGap.SMALL)

    val headerGroup = collapsibleGroup(title = KafkaMessagesBundle.message("record.info.headers"), indent = false) {
      row {
        cell(headers.getComponent()).align(AlignX.FILL).resizableColumn()
      }
    }
    headerGroup.expanded = false
    headerGroup.topGap(TopGap.SMALL).bottomGap(BottomGap.NONE)

    val metainfoGroup = collapsibleGroup(title = KafkaMessagesBundle.message("record.info.metadata"), indent = true) {
      row(KafkaMessagesBundle.message("consumer.record.topic")) {
        cell(topicField).align(AlignX.FILL)
      }

      row(KafkaMessagesBundle.message("consumer.record.partition")) {
        cell(partition).align(AlignX.FILL)
      }

      row(KafkaMessagesBundle.message("consumer.record.offset")) {
        cell(offset).align(AlignX.FILL)
      }
      row(KafkaMessagesBundle.message("consumer.record.timestamp")) {
        cell(timestamp).align(AlignX.FILL)
      }

      row(KafkaMessagesBundle.message("consumer.record.keysize")) {
        cell(keySize).align(AlignX.FILL)
      }

      row(KafkaMessagesBundle.message("consumer.record.valuesize")) {
        cell(valueSize).align(AlignX.FILL)
      }

      row(KafkaMessagesBundle.message("label.key.type")) {
        cell(keyTypeLabel).align(AlignX.FILL)
      }

      row(KafkaMessagesBundle.message("label.value.type")) {
        cell(valueTypeLabel).align(AlignX.FILL)
      }

    }
    metainfoGroup.expanded = false
    metainfoGroup.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)


  }).apply {
    border = BorderFactory.createEmptyBorder()
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }

  init {
    update(null)
  }

  fun update(row: KafkaRecord?) {
    keyType = row?.keyType ?: KafkaFieldType.STRING
    valueType = row?.valueType ?: KafkaFieldType.JSON


    if (row == null) {
      setFieldValue(KafkaFieldType.STRING, "", true)
      setFieldValue(KafkaFieldType.STRING, "", false)

      topicField.text = ""
      partition.text = ""
      offset.text = ""
      timestamp.text = ""
      keySize.text = ""
      valueSize.text = ""

      keyTypeLabel.text = ""
      valueTypeLabel.text = ""

      headers.clear()
    }
    else {
      setFieldValue(row.keyType, row.keyText ?: "", true)
      setFieldValue(row.valueType, row.valueText ?: "", false)

      topicField.text = row.topic
      partition.text = if (row.partition >= 0) row.partition.toString() else ""
      offset.text = if (row.offset >= 0) row.offset.toString() else ""
      timestamp.text = TimeUtils.unixTimeToString(row.timestamp)
      keySize.text = SizeUtils.toString(row.keySize)
      valueSize.text = SizeUtils.toString(row.valueSize)
      keyTypeLabel.text = keyType.title
      valueTypeLabel.text = valueType.title

      headers.properties = row.headers.toMutableList()
    }

    // Key and value Fields could contain multiline JSON
    component.revalidate()
  }

  private fun setFieldValue(fieldType: KafkaFieldType, value: String, isKey: Boolean) {
    val viewerType = if (isKey) keyViewerType else valueViewerType
    val textField = if (isKey) keyFieldText else valueFieldText
    val textScrollableField = if (isKey) keyFieldTextScroll else valueFieldTextScroll
    val jsonField = if (isKey) keyFieldJson else valueFieldJson
    val linkRow = if (isKey) keyLoadFileLinkRow else valueLoadFileLinkRow

    textField.text = value

    jsonField.document.setReadOnly(false)
    jsonField.text = KafkaEditorUtils.tryFormatJson(value)
    jsonField.document.setReadOnly(true)

    updateViewerVisible(viewerType, fieldType, textScrollableField, jsonField, linkRow)
  }


  private fun updateViewerVisible(viewerType: ComboBox<FieldViewerType>,
                                  fieldType: KafkaFieldType,
                                  textField: AdjustableScrollPanel,
                                  jsonField: EditorTextField,
                                  linkRow: Row) {
    val visibleFieldType = if (viewerType.item === FieldViewerType.AUTO) {
      detectAutoType(fieldType, jsonField.text)
    }
    else {
      viewerType.item
    }

    textField.isVisible = visibleFieldType == FieldViewerType.TEXT || visibleFieldType == FieldViewerType.DECODED_BASE64
    jsonField.isVisible = visibleFieldType == FieldViewerType.JSON
    linkRow.visible(visibleFieldType == FieldViewerType.DECODED_BASE64)
  }

  private fun detectAutoType(fieldType: KafkaFieldType, text: String): FieldViewerType = when (fieldType) {
    KafkaFieldType.STRING -> if (KafkaEditorUtils.isJsonString(text)) FieldViewerType.JSON else FieldViewerType.TEXT
    KafkaFieldType.JSON -> FieldViewerType.JSON
    KafkaFieldType.LONG -> FieldViewerType.TEXT
    KafkaFieldType.DOUBLE -> FieldViewerType.TEXT
    KafkaFieldType.FLOAT -> FieldViewerType.TEXT
    KafkaFieldType.BASE64 -> FieldViewerType.DECODED_BASE64
    KafkaFieldType.NULL -> FieldViewerType.TEXT
    KafkaFieldType.SCHEMA_REGISTRY -> FieldViewerType.JSON
  }

  inner class ConsumerEditorCustomization : EditorCustomization {
    override fun customize(editor: EditorEx) {

      editor.scrollPane.layout = object : JBScrollPane.Layout() {
        override fun preferredLayoutSize(parent: Container?): Dimension {
          val superSize = super.preferredLayoutSize(parent)

          return Dimension(superSize.width,
                           min(JBUIScale.scale(500),
                               superSize.height + (if (horizontalScrollBar?.isVisible == true) horizontalScrollBar.height * 3 else 0)))
        }
      }
    }
  }

  private fun loadBinaryFile(project: Project, defaultFileName: String, fieldText: JTextArea) {
    val virtualFile = FileChooserUtil.selectFolderAndCreateFile(project, defaultFileName) ?: return
    runWriteAction {
      virtualFile.writeBytes(Base64.getDecoder().decode(fieldText.text))
    }
  }
}
