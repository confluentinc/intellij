package com.jetbrains.bigdatatools.kafka.ui


import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.ui.MigPanel
import net.miginfocom.layout.CC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.beans.PropertyChangeListener
import java.io.Serializable
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent

class KafkaConsumerEditor(project: Project,
                          kafkaManager: KafkaDataManager,
                          private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private val consumerClient = kafkaManager.client.createConsumerClient()
  val topics = kafkaManager.getTopics()

  init {
    Disposer.register(this, consumerClient)
  }

  private val topicComboBox = ComboBox(topics.toTypedArray()).apply { renderer = TopicRenderer() }
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


  private val outputModel = DefaultListModel<ConsumerRecord<Serializable, Serializable>>()
  private val outputList = JBList(outputModel).apply {
    setCellRenderer(ConsumerOutputRender())
  }

  private val consumeButton = JButton(KafkaMessagesBundle.message("action.consume.start.title")).apply {
    addActionListener {
      if (consumerClient.isRunning()) {
        consumerClient.stop()
        text = KafkaMessagesBundle.message("action.consume.start.title")
      }
      else {
        startConsume()
        text = KafkaMessagesBundle.message("action.consume.stop.title")

      }
      updateVisibility()
      invalidate()
      repaint()
    }

  }

  private val mainComponent = createCenterPanel()

  init {
    updateVisibility()
  }

  private fun createCenterPanel() = MigPanel().apply {
    row("Topics:", topicComboBox)
    row("Key:", keyComboBox)
    row("Value:", valueComboBox)
    add(consumeButton, CC().spanX().growX().wrap())
    add(outputList, CC().spanX().growX().wrap())
  }

  private fun startConsume() {
    val topic = topicComboBox.item ?: error("Topic is not selected")

    consumerClient.start(topic.name) { record: ConsumerRecord<Serializable, Serializable> ->
      outputModel.addElement(record)
    }
  }

  private fun updateVisibility() {
    val isEnabled = !consumerClient.isRunning()
    topicComboBox.isEnabled = isEnabled
    keyComboBox.isEnabled = isEnabled
    valueComboBox.isEnabled = isEnabled
  }

  override fun getName(): String = KafkaMessagesBundle.message("consume.from.topic")
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