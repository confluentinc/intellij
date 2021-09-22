package com.jetbrains.bigdatatools.kafka.ui


import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.ui.MigPanel
import com.michaelbaranov.microba.calendar.DatePicker
import net.miginfocom.layout.CC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.beans.PropertyChangeListener
import java.io.Serializable
import java.util.*
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent

class KafkaConsumerEditor(kafkaManager: KafkaDataManager,
                          private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private val consumerClient = kafkaManager.client.createConsumerClient()
  val topics = kafkaManager.getTopics()

  private val startSpecificDate = DatePicker()
  private val limitSpecificDate = DatePicker()
  private val limitOffset = JBTextField()

  private val startOffset = JBTextField()
  private val startFromComboBox = ComboBox(ConsumerStartFrom.values()).apply {
    renderer = StartFromRenderer()
    item = ConsumerStartFrom.NOW
    addItemListener {
      updateStartWith()
    }
  }

  private val limitComboBox = ComboBox(ConsumerLimit.values()).apply {
    renderer = LimitRenderer()
    item = ConsumerLimit.NONE
    addItemListener {
      updateLimit()
    }
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
    Disposer.register(this, consumerClient)
    updateVisibility()
    updateLimit()
    updateStartWith()
  }

  private fun createCenterPanel() = MigPanel().apply {
    row("Topics:", topicComboBox)
    row("Key:", keyComboBox)
    row("Value:", valueComboBox)

    row("Start from:", startFromComboBox)
    add(startSpecificDate, CC().spanX().growX().wrap())
    add(startOffset, CC().spanX().growX().wrap())

    row("Limit:", limitComboBox)
    add(limitSpecificDate, CC().spanX().growX().wrap())
    add(limitOffset, CC().spanX().growX().wrap())

    add(consumeButton, CC().spanX().growX().wrap())

    add(outputList, CC().spanX().growX().wrap())
  }

  private fun startConsume() {
    val topic = topicComboBox.item ?: error("Topic is not selected")

    val startOffset: Long? = when (startFromComboBox.selectedItem) {
      ConsumerStartFrom.OFFSET -> startOffset.text.ifBlank { null }?.toLongOrNull()
      ConsumerStartFrom.LATEST_OFFSET_MINUS_X -> startOffset.text.ifBlank { null }?.toLongOrNull()?.times(-1)
      ConsumerStartFrom.THE_BEGINNING -> 0
      else -> startOffset.text.ifBlank { null }?.toLongOrNull()
    }

    val calendar = Calendar.getInstance()
    calendar.time = Date()

    val startTime = when (startFromComboBox.selectedItem) {
      ConsumerStartFrom.NOW -> calendar.time
      ConsumerStartFrom.LAST_HOUR -> {
        calendar.add(Calendar.HOUR_OF_DAY, -1)
        calendar.time
      }
      ConsumerStartFrom.TODAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.time
      }
      ConsumerStartFrom.YESTERDAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.time
      }

      ConsumerStartFrom.SPECIFIC_DATE -> startSpecificDate.date
      else -> null
    }

    consumerClient.start(topic = topic.name,
                         startOffset = startOffset,
                         startTimeMs = startTime?.time,
                         limitTime = getLimitTime(),
                         partitionLimitSize = getLimitPartitionsSize(),
                         topicLimitSize = getLimitTopicSize(),
                         topicLimitCount = getLimitTopicCount(),
                         partitionLimitCount = getLimitPartitionCount()) { record ->
      outputModel.addElement(record)
    }
  }

  private fun updateVisibility() {
    val isEnabled = !consumerClient.isRunning()
    topicComboBox.isEnabled = isEnabled
    keyComboBox.isEnabled = isEnabled
    valueComboBox.isEnabled = isEnabled
    startSpecificDate.isEnabled = isEnabled
    startOffset.isEnabled = isEnabled
    limitOffset.isEnabled = isEnabled
    limitSpecificDate.isEnabled = isEnabled
  }

  private fun updateStartWith() {
    startSpecificDate.isVisible = false
    startOffset.isVisible = false
    when (startFromComboBox.selectedItem) {
      ConsumerStartFrom.SPECIFIC_DATE -> startSpecificDate.isVisible = true
      ConsumerStartFrom.OFFSET, ConsumerStartFrom.LATEST_OFFSET_MINUS_X -> startOffset.isVisible = true
    }
  }

  private fun updateLimit() {
    limitSpecificDate.isVisible = false
    limitOffset.isVisible = false

    when (limitComboBox.selectedItem) {
      ConsumerLimit.DATE -> limitSpecificDate.isVisible = true
      ConsumerLimit.TOPIC_NUMBER_RECORDS,
      ConsumerLimit.PARTITION_NUMBER_RECORDS,
      ConsumerLimit.PARTITION_MAX_SIZE,
      ConsumerLimit.TOPIC_MAX_SIZE -> limitOffset.isVisible = true
    }
  }


  private fun getLimitTime() = if (limitComboBox.selectedItem == ConsumerLimit.DATE)
    limitSpecificDate.date.time
  else
    null

  private fun getLimitTopicCount() = if (limitComboBox.selectedItem == ConsumerLimit.TOPIC_NUMBER_RECORDS)
    limitOffset.text?.toLongOrNull()
  else
    null

  private fun getLimitTopicSize() = if (limitComboBox.selectedItem == ConsumerLimit.TOPIC_MAX_SIZE)
    limitOffset.text?.toLongOrNull()
  else
    null

  private fun getLimitPartitionCount() = if (limitComboBox.selectedItem == ConsumerLimit.PARTITION_NUMBER_RECORDS)
    limitOffset.text?.toLongOrNull()
  else
    null

  private fun getLimitPartitionsSize() = if (limitComboBox.selectedItem == ConsumerLimit.PARTITION_MAX_SIZE)
    limitOffset.text?.toLongOrNull()
  else
    null


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