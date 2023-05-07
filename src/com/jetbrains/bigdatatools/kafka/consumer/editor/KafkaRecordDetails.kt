package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.common.rfs.driver.metainfo.components.SelectableLabel
import com.jetbrains.bigdatatools.common.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.util.SizeUtils
import com.jetbrains.bigdatatools.common.util.TimeUtils
import com.jetbrains.bigdatatools.kafka.common.editor.FieldViewerType
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import kotlin.math.min

class KafkaRecordDetails(project: Project, parentDisposable: Disposable) {
  private val topicField = SelectableLabel("")
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

  private var keyType = FieldType.JSON
  private var valueType = FieldType.JSON

  init {
    keyFieldJson = KafkaEditorUtils.createJsonTextArea(project, listOf(ConsumerEditorCustomization())).apply {
      document.setReadOnly(true)
      setDisposedWith(parentDisposable)
    }

    valueFieldJson = KafkaEditorUtils.createJsonTextArea(project, listOf(ConsumerEditorCustomization())).apply {
      document.setReadOnly(true)
      setDisposedWith(parentDisposable)
    }

    keyViewerType.addActionListener {
      updateViewerVisible(keyViewerType, keyType, keyFieldTextScroll, keyFieldJson)
      component.revalidate()
    }

    valueViewerType.addActionListener {
      updateViewerVisible(valueViewerType, valueType, valueFieldTextScroll, valueFieldJson)
      component.revalidate()
    }


  }

  val component = JBScrollPane(panel {
    row(KafkaMessagesBundle.message("consumer.record.key")) {
      cell(keyViewerType).align(AlignX.RIGHT)
    }
    row {
      cell(keyFieldJson).resizableColumn().align(AlignX.FILL)
      cell(keyFieldTextScroll).resizableColumn().align(AlignX.FILL)
    }.bottomGap(BottomGap.SMALL)

    row(KafkaMessagesBundle.message("consumer.record.value")) {
      cell(valueViewerType).align(AlignX.RIGHT)
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
        cell(topicField)
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
    keyType = row?.keyType ?: FieldType.STRING
    valueType = row?.valueType ?: FieldType.JSON


    if (row == null) {
      setFieldValue(FieldType.STRING, "", true)
      setFieldValue(FieldType.STRING, "", false)

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

  private fun setFieldValue(fieldType: FieldType, value: String, isKey: Boolean) {
    val viewerType = if (isKey) keyViewerType else valueViewerType
    val textField = if (isKey) keyFieldText else valueFieldText
    val textScrollableField = if (isKey) keyFieldTextScroll else valueFieldTextScroll
    val jsonField = if (isKey) keyFieldJson else valueFieldJson

    textField.text = value

    jsonField.document.setReadOnly(false)
    jsonField.text = KafkaEditorUtils.tryFormatJson(value)
    jsonField.document.setReadOnly(true)

    updateViewerVisible(viewerType, fieldType, textScrollableField, jsonField)
  }


  private fun updateViewerVisible(viewerType: ComboBox<FieldViewerType>,
                                  fieldType: FieldType,
                                  textField: AdjustableScrollPanel,
                                  jsonField: EditorTextField) {
    val isString = when (viewerType.item) {
      FieldViewerType.AUTO -> shouldBeString(fieldType, jsonField.text)
      FieldViewerType.TEXT -> true
      FieldViewerType.JSON -> false
      FieldViewerType.DECODED_BASE64 -> true
      else -> false
    }

    textField.isVisible = isString
    jsonField.isVisible = !isString
  }

  private fun shouldBeString(fieldType: FieldType, text: String) = when (fieldType) {
    FieldType.STRING -> !KafkaEditorUtils.isJsonString(text)
    FieldType.JSON -> false
    FieldType.LONG -> true
    FieldType.DOUBLE -> true
    FieldType.FLOAT -> true
    FieldType.BASE64 -> true
    FieldType.NULL -> true
    FieldType.AVRO_REGISTRY -> false
    FieldType.PROTOBUF_REGISTRY -> false
    FieldType.JSON_REGISTRY -> false
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

  // Special scroll pane used for text presentation of keys and values.
  inner class AdjustableScrollPanel(view: Component) : JBScrollPane(view) {
    init {
      // In the other case the borders will be removed when the component placed in the Editor.
      putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL)
      border = BorderFactory.createLineBorder(JBColor.border())
    }

    override fun getPreferredSize(): Dimension {
      val superSize = super.getPreferredSize()
      return Dimension(superSize.width,
                       min(JBUIScale.scale(500),
                           superSize.height + (if (horizontalScrollBar?.isVisible == true) horizontalScrollBar.height * 2 else 0)))
    }
  }
}