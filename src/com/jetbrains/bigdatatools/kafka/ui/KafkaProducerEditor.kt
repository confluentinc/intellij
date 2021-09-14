package com.jetbrains.bigdatatools.kafka.ui


import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization
import com.intellij.ui.components.JBList
import com.intellij.ui.components.fields.IntegerField
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.ui.MigPanel
import net.miginfocom.layout.CC
import java.beans.PropertyChangeListener
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextField

class KafkaProducerEditor(project: Project,
                          kafkaManager: KafkaDataManager,
                          private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private val producerClient = kafkaManager.client.createProducerClient()
  val topics = kafkaManager.getTopics()

  private val propertiesComponent = BdtPropertyComponent("", label = KafkaMessagesBundle.message("record.headers.label"))

  private val topicComboBox = ComboBox(topics.toTypedArray()).apply { renderer = TopicRenderer() }

  private val compressionComboBox = ComboBox(RecordCompression.values()).apply {
    renderer = RecordCompressionRenderer()
    selectedIndex = 0
  }
  private val keyComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    addItemListener {
      updateVisibility()
    }
  }
  private val valueComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    addItemListener {
      updateVisibility()
    }
  }

  private val keyJson = createJsonTextArea(project)
  private val valueJson = createJsonTextArea(project)

  private val keyIntegerField = IntegerField()
  private val valueIntegerField = IntegerField()

  private val keyDoubleField = doubleField()
  private val valueDoubleField = doubleField()

  private val keyStringField = JTextField()
  private val valueStringField = JTextField()


  private val outputModel = DefaultListModel<ProducerResultMessage>()
  private val outputList = JBList(outputModel).apply {
    setCellRenderer(ProducerOutputRender())
  }

  private val produceButton = JButton(KafkaMessagesBundle.message("kafka.producer.action,produce.title")).also {
    it.addActionListener {
      val topic = topicComboBox.item ?: error("Topic is not selected")
      val selectedTopicName = topic.name

      val key = KafkaField(keyComboBox.item!!, getKey())
      val value = KafkaField(valueComboBox.item!!, getValue())

      val result = producerClient.sentMessage(selectedTopicName, key, value, propertiesComponent.getProperties(), compressionComboBox.item)
      outputModel.addElement(result)
    }
  }

  private val mainComponent = createCenterPanel()

  init {
    updateVisibility()
  }

  private fun createCenterPanel() = MigPanel().apply {
    row("Topics:", topicComboBox)

    row("Key:", keyComboBox)
    add(keyJson, CC().spanX().growX().wrap())
    add(keyIntegerField, CC().spanX().growX().wrap())
    add(keyDoubleField, CC().spanX().growX().wrap())
    add(keyStringField, CC().spanX().growX().wrap())

    row("Value:", valueComboBox)
    add(valueJson, CC().spanX().growX().wrap())
    add(valueIntegerField, CC().spanX().growX().wrap())
    add(valueDoubleField, CC().spanX().growX().wrap())
    add(valueStringField, CC().spanX().growX().wrap())

    row(propertiesComponent.label, propertiesComponent.getComponent())

    row("Compression type:", compressionComboBox)
    add(produceButton, CC().spanX().growX().wrap())
    add(outputList, CC().spanX().growX().wrap())
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
      FieldType.NULL -> {
      }
    }

    @Suppress("DuplicatedCode")
    when (valueComboBox.item!!) {
      FieldType.JSON -> valueJson.isVisible = true
      FieldType.STRING -> valueStringField.isVisible = true
      FieldType.LONG -> valueIntegerField.isVisible = true
      FieldType.DOUBLE -> valueDoubleField.isVisible = true
      FieldType.FLOAT -> valueDoubleField.isVisible = true
      FieldType.BASE64 -> valueStringField.isVisible = true
      FieldType.NULL -> {
      }
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

  override fun getName(): String = "Produce to Topic"
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