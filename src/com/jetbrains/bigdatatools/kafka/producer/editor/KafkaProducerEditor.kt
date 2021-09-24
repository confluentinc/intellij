package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.IntegerField
import com.jetbrains.bigdatatools.kafka.common.editor.renders.FieldTypeRenderer
import com.jetbrains.bigdatatools.kafka.common.editor.renders.TopicRenderer
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.KafkaField
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.editor.renders.AcksRenderer
import com.jetbrains.bigdatatools.kafka.producer.editor.renders.ProducerOutputRender
import com.jetbrains.bigdatatools.kafka.producer.editor.renders.RecordCompressionRenderer
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerResultMessage
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.MigPanel
import net.miginfocom.layout.LC
import java.beans.PropertyChangeListener
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent

class KafkaProducerEditor(project: Project,
                          kafkaManager: KafkaDataManager,
                          private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private val producerClient = kafkaManager.client.createProducerClient()
  val topics = kafkaManager.getTopics()

  private val propertiesComponent = BdtPropertyComponent("", label = KafkaMessagesBundle.message("record.headers.label"))

  private val topicComboBox = ComboBox(topics.toTypedArray()).apply { renderer = TopicRenderer() }
  private val acksComboBox = ComboBox(AcksType.values()).apply {
    renderer = AcksRenderer()
    item = AcksType.NONE
  }
  private val idempotenceCheckBox = CheckBox(KafkaMessagesBundle.message("producer.idempotence.label")).apply {
    addChangeListener {
      acksComboBox.isEnabled = !isSelected
    }
  }
  private val compressionComboBox = ComboBox(RecordCompression.values()).apply {
    renderer = RecordCompressionRenderer()
    selectedIndex = 0
  }
  private val keyComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
    }
  }
  private val valueComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
    }
  }

  private val keyJson = createJsonTextArea(project)
  private val valueJson = createJsonTextArea(project)

  private val keyIntegerField = IntegerField().apply { emptyText.text = "Optional" }
  private val valueIntegerField = IntegerField().apply { emptyText.text = "Optional" }

  private val keyDoubleField = doubleField().apply { emptyText.text = "Optional" }
  private val valueDoubleField = doubleField().apply { emptyText.text = "Optional" }

  private val keyStringField = JBTextField().apply { emptyText.text = "Optional" }
  private val valueStringField = JBTextField().apply { emptyText.text = "Optional" }

  private val forcePartitionField = IntegerField().apply {
    isCanBeEmpty = true
    defaultValue = -1
  }

  private val outputModel = DefaultListModel<ProducerResultMessage>()
  private val outputList = JBList(outputModel).apply {
    setCellRenderer(ProducerOutputRender())
  }

  private val produceButton = JButton(KafkaMessagesBundle.message("kafka.producer.action.produce.title")).also {
    it.addActionListener {
      val topic = topicComboBox.item ?: error("Topic is not selected")
      val selectedTopicName = topic.name

      val key = KafkaField(keyComboBox.item!!, getKey())
      val value = KafkaField(valueComboBox.item!!, getValue())

      val result = producerClient.sentMessage(selectedTopicName, key, value,
                                              propertiesComponent.getProperties(),
                                              compressionComboBox.item,
                                              acksComboBox.item,
                                              idempotenceCheckBox.isSelected,
                                              forcePartitionField.value)
      outputModel.addElement(result)
    }
  }

  private val clearButton = JButton(KafkaMessagesBundle.message("action.clear.output")).apply {
    addActionListener {
      outputModel.clear()
    }
  }

  private val mainComponent = createCenterPanel()

  init {
    updateVisibility()
  }

  private fun createCenterPanel() = OnePixelSplitter().apply {

    val leftPanel = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {

      gapLeft = true
      title("Data")
      row("Topics:", topicComboBox)
      row("Key:", keyComboBox)
      add(keyJson, UiUtil.growXSpanXWrap)
      add(keyIntegerField, UiUtil.growXSpanXWrap)
      add(keyDoubleField, UiUtil.growXSpanXWrap)
      add(keyStringField, UiUtil.growXSpanXWrap)

      row("Value:", valueComboBox)
      add(valueJson, UiUtil.growXSpanXWrap)
      add(valueIntegerField, UiUtil.growXSpanXWrap)
      add(valueDoubleField, UiUtil.growXSpanXWrap)
      add(valueStringField, UiUtil.growXSpanXWrap)

      title("Options")
      row("Force partition:", forcePartitionField)
      row(propertiesComponent.label, propertiesComponent.getComponent())

      row("Compression:", compressionComboBox)
      row("Acks:", acksComboBox)
      add(idempotenceCheckBox, UiUtil.gapLeftSpanXWrap)

      gapLeft = false

      add(produceButton, UiUtil.growXSpanXWrap)
      add(clearButton, UiUtil.growXSpanXWrap)
    }

    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE

    firstComponent = leftPanel
    secondComponent = JBScrollPane(outputList)

    proportion = 0.1f
  }

  private fun createJsonTextArea(project: Project) = EditorTextFieldProvider
    .getInstance()
    .getEditorField(JsonLanguage.INSTANCE, project,
                    listOf(EditorCustomization {
                      it.settings.apply {
                        isLineNumbersShown = false
                        isLineMarkerAreaShown = false
                        isFoldingOutlineShown = false
                        isRightMarginShown = false
                        additionalLinesCount = 5
                        additionalColumnsCount = 5
                        isAdditionalPageAtBottom = false
                        isShowIntentionBulb = false
                      }
                    }, MonospaceEditorCustomization.getInstance()))

  private fun doubleField() = IntegerField()

  private fun updateVisibility() {
    keyJson.isVisible = false
    valueJson.isVisible = false

    keyIntegerField.isVisible = false
    valueIntegerField.isVisible = false

    keyStringField.isVisible = false
    valueStringField.isVisible = false

    keyDoubleField.isVisible = false
    valueDoubleField.isVisible = false

    @Suppress("DuplicatedCode")
    when (keyComboBox.item!!) {
      FieldType.JSON -> keyJson.isVisible = true
      FieldType.STRING -> keyStringField.isVisible = true
      FieldType.LONG -> keyIntegerField.isVisible = true
      FieldType.DOUBLE -> keyDoubleField.isVisible = true
      FieldType.FLOAT -> keyDoubleField.isVisible = true
      FieldType.BASE64 -> keyStringField.isVisible = true
      FieldType.NULL -> Unit
    }

    @Suppress("DuplicatedCode")
    when (valueComboBox.item!!) {
      FieldType.JSON -> valueJson.isVisible = true
      FieldType.STRING -> valueStringField.isVisible = true
      FieldType.LONG -> valueIntegerField.isVisible = true
      FieldType.DOUBLE -> valueDoubleField.isVisible = true
      FieldType.FLOAT -> valueDoubleField.isVisible = true
      FieldType.BASE64 -> valueStringField.isVisible = true
      FieldType.NULL -> Unit
    }
  }

  private fun getKey() = when (keyComboBox.item!!) {
    FieldType.JSON -> keyJson.text
    FieldType.STRING -> keyStringField.text
    FieldType.LONG -> keyIntegerField.text
    FieldType.DOUBLE -> keyDoubleField.text
    FieldType.FLOAT -> keyDoubleField.text
    FieldType.BASE64 -> keyStringField.text
    FieldType.NULL -> null
  }

  private fun getValue() = when (valueComboBox.item!!) {
    FieldType.JSON -> valueJson.text
    FieldType.STRING -> valueStringField.text
    FieldType.LONG -> valueIntegerField.text
    FieldType.DOUBLE -> valueDoubleField.text
    FieldType.FLOAT -> valueDoubleField.text
    FieldType.BASE64 -> valueStringField.text
    FieldType.NULL -> null
  }

  override fun getName(): String = KafkaMessagesBundle.message("produce.to.topic")
  override fun getComponent(): JComponent = mainComponent
  override fun getPreferredFocusedComponent(): JComponent = mainComponent
  override fun getFile(): VirtualFile = file
  override fun setState(state: FileEditorState) = Unit
  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun dispose() {}
}