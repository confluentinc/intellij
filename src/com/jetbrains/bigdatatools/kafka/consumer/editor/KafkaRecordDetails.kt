package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.writeBytes
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.rfs.driver.metainfo.components.SelectableLabel
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.DarculaTextAreaBorder
import com.jetbrains.bigdatatools.common.ui.chooser.FileChooserUtil
import com.jetbrains.bigdatatools.common.util.SizeUtils
import com.jetbrains.bigdatatools.common.util.TimeUtils
import com.jetbrains.bigdatatools.kafka.common.editor.FieldViewerType
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaRegistrySchemaEditor
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.CardLayout
import java.util.*
import javax.swing.*

class KafkaRecordDetails(project: Project, parentDisposable: Disposable) {
  private val topicField = SelectableLabel("")

  private lateinit var keyLoadFileLinkRow: Row
  private lateinit var valueLoadFileLinkRow: Row

  private val keyViewerType = ComboBox(FieldViewerType.entries.toTypedArray()).apply {
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }
  private val keyFieldJson = KafkaRegistrySchemaEditor(project, parentDisposable, isEditable = false).apply {
    component.border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
  }

  private val valueViewerType = ComboBox(FieldViewerType.entries.toTypedArray()).apply {
    renderer = CustomListCellRenderer<FieldViewerType> { it.title }
  }
  private val valueFieldJson = KafkaRegistrySchemaEditor(project, parentDisposable, isEditable = false).apply {
    component.border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
  }

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

  private val emptyStatePanel = JPanel(BorderLayout()).apply {
    border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
    add(JLabel("<html>${KafkaMessagesBundle.message("consumer.details.empty")}</html>", SwingConstants.CENTER).apply {
      isEnabled = false
    })
  }

  private val detailsPanel = JBScrollPane(panel {
    row(KafkaMessagesBundle.message("consumer.record.key")) {
      cell(keyViewerType).align(AlignX.RIGHT)
    }
    keyLoadFileLinkRow = row {
      link(KafkaMessagesBundle.message("producer.config.link.load.file")) {
        try {
          loadBinaryFile(project, "key", keyFieldJson)
        }
        catch (t: Throwable) {
          RfsNotificationUtils.showExceptionMessage(project, t)
        }
      }
    }

    row {
      cell(keyFieldJson.component).resizableColumn().align(AlignX.FILL)
    }.bottomGap(BottomGap.SMALL)

    row(KafkaMessagesBundle.message("consumer.record.value")) {
      cell(valueViewerType).align(AlignX.RIGHT)
    }
    valueLoadFileLinkRow = row {
      link(KafkaMessagesBundle.message("producer.config.link.load.file")) {
        loadBinaryFile(project, "value", valueFieldJson)
      }
    }
    row {
      cell(valueFieldJson.component).resizableColumn().align(AlignX.FILL)
    }.bottomGap(BottomGap.SMALL)

    val headerGroup = collapsibleGroup(title = KafkaMessagesBundle.message("record.info.headers"), indent = false) {
      row {
        cell(headers.getComponent()).align(AlignX.FILL).resizableColumn()
      }
    }
    headerGroup.expanded = false
    headerGroup.topGap(TopGap.SMALL).bottomGap(BottomGap.NONE)

    val metainfoGroup = collapsibleGroup(title = KafkaMessagesBundle.message("record.info.metadata"), indent = true) {
      row(KafkaMessagesBundle.message("consumer.record.topic")) { cell(topicField).align(AlignX.FILL) }
      row(KafkaMessagesBundle.message("consumer.record.partition")) { cell(partition).align(AlignX.FILL) }
      row(KafkaMessagesBundle.message("consumer.record.offset")) { cell(offset).align(AlignX.FILL) }
      row(KafkaMessagesBundle.message("consumer.record.timestamp")) { cell(timestamp).align(AlignX.FILL) }
      row(KafkaMessagesBundle.message("consumer.record.keysize")) { cell(keySize).align(AlignX.FILL) }
      row(KafkaMessagesBundle.message("consumer.record.valuesize")) { cell(valueSize).align(AlignX.FILL) }
      row(KafkaMessagesBundle.message("label.key.type")) { cell(keyTypeLabel).align(AlignX.FILL) }
      row(KafkaMessagesBundle.message("label.value.type")) { cell(valueTypeLabel).align(AlignX.FILL) }
    }

    metainfoGroup.expanded = false
    metainfoGroup.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)

  }).apply {
    border = BorderFactory.createEmptyBorder()
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }

  val component = JPanel(CardLayout()).apply {
    add(emptyStatePanel, "emptyState")
    add(detailsPanel, "details")
  }

  init {
    keyViewerType.addActionListener {
      updateViewerVisible(keyViewerType, keyType, keyFieldJson)
      updateLinkRow(keyViewerType, keyType, keyFieldJson, keyLoadFileLinkRow)
      emptyStatePanel.revalidate()
    }

    valueViewerType.addActionListener {
      updateViewerVisible(valueViewerType, valueType, valueFieldJson)
      updateLinkRow(valueViewerType, valueType, valueFieldJson, valueLoadFileLinkRow)
      emptyStatePanel.revalidate()
    }

    headers.getComponent().border = IdeBorderFactory.createBorder(SideBorder.RIGHT or SideBorder.BOTTOM or SideBorder.LEFT)

    update(null)

    updateLinkRow(keyViewerType, keyType, keyFieldJson, keyLoadFileLinkRow)
    updateLinkRow(valueViewerType, valueType, valueFieldJson, valueLoadFileLinkRow)
  }

  private fun setKeyText(text: String, fieldType: KafkaFieldType) {
    setFieldValue(keyViewerType, fieldType, keyFieldJson, text)
  }

  private fun setValueText(text: String, fieldType: KafkaFieldType) {
    setFieldValue(valueViewerType, fieldType, valueFieldJson, text)
  }

  fun update(row: KafkaRecord?) {
    keyType = row?.keyType ?: KafkaFieldType.STRING
    valueType = row?.valueType ?: KafkaFieldType.JSON

    (component.layout as? CardLayout)?.show(component, if (row == null) "emptyState" else "details")

    if (row != null) {
      setKeyText(row.keyText ?: "", row.keyType)
      setValueText(row.valueText ?: "", row.valueType)

      topicField.text = row.topic
      partition.text = if (row.partition >= 0) row.partition.toString() else ""
      offset.text = if (row.offset >= 0) row.offset.toString() else ""
      timestamp.text = TimeUtils.unixTimeToString(row.timestamp)
      keySize.text = SizeUtils.toString(row.keySize)
      valueSize.text = SizeUtils.toString(row.valueSize)
      keyTypeLabel.text = keyType.title
      valueTypeLabel.text = valueType.title

      headers.properties = row.headers.toMutableList()

      // Key and value Fields could contain multiline JSON
      detailsPanel.revalidate()
    }
  }

  private fun updateLinkRow(viewerType: ComboBox<FieldViewerType>,
                            fieldType: KafkaFieldType,
                            jsonField: KafkaRegistrySchemaEditor,
                            linkRow: Row) {
    val visibleFieldType = if (viewerType.item === FieldViewerType.AUTO) {
      detectAutoType(fieldType, jsonField.text)
    }
    else {
      viewerType.item
    }

    linkRow.visible(visibleFieldType == FieldViewerType.DECODED_BASE64)
  }

  private fun setFieldValue(viewerType: ComboBox<FieldViewerType>,
                            fieldType: KafkaFieldType,
                            jsonField: KafkaRegistrySchemaEditor,
                            value: String) {
    val visibleFieldType = getFieldType(viewerType, fieldType, jsonField)
    if (visibleFieldType == FieldViewerType.JSON) {
      jsonField.setText(KafkaEditorUtils.tryFormatJson(value), JsonLanguage.INSTANCE)
    }
    else {
      jsonField.setText(value, PlainTextLanguage.INSTANCE)
    }
  }

  // Real field type depends on original consumer field type and additional Details field type which can override consumer values.
  private fun getFieldType(viewerType: ComboBox<FieldViewerType>,
                           fieldType: KafkaFieldType,
                           jsonField: KafkaRegistrySchemaEditor): FieldViewerType {
    return if (viewerType.item === FieldViewerType.AUTO) {
      detectAutoType(fieldType, jsonField.text)
    }
    else {
      viewerType.item
    }
  }

  private fun updateViewerVisible(viewerType: ComboBox<FieldViewerType>,
                                  fieldType: KafkaFieldType,
                                  jsonField: KafkaRegistrySchemaEditor) {
    val visibleFieldType = getFieldType(viewerType, fieldType, jsonField)
    jsonField.setLanguage(if (visibleFieldType == FieldViewerType.JSON) JsonLanguage.INSTANCE else PlainTextLanguage.INSTANCE)
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
    KafkaFieldType.PROTOBUF_CUSTOM -> FieldViewerType.JSON
    KafkaFieldType.AVRO_CUSTOM -> FieldViewerType.JSON
  }

  private fun loadBinaryFile(project: Project, defaultFileName: String, fieldText: KafkaRegistrySchemaEditor) {
    val virtualFile = FileChooserUtil.selectFolderAndCreateFile(project, defaultFileName) ?: return
    runWriteAction {
      virtualFile.writeBytes(Base64.getDecoder().decode(fieldText.text))
    }
  }
}
