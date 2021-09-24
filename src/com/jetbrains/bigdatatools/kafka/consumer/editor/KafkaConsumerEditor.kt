package com.jetbrains.bigdatatools.kafka.consumer.editor


import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.jetbrains.bigdatatools.kafka.consumer.client.KafkaConsumerClient
import com.jetbrains.bigdatatools.kafka.consumer.editor.renders.FilterRenderer
import com.jetbrains.bigdatatools.kafka.consumer.editor.renders.LimitRenderer
import com.jetbrains.bigdatatools.kafka.consumer.editor.renders.StartFromRenderer
import com.jetbrains.bigdatatools.kafka.consumer.models.*
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.ui.ConsumerOutputRender
import com.jetbrains.bigdatatools.kafka.ui.FieldType
import com.jetbrains.bigdatatools.kafka.ui.FieldTypeRenderer
import com.jetbrains.bigdatatools.kafka.ui.TopicRenderer
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.MigPanel
import com.michaelbaranov.microba.calendar.DatePicker
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.beans.PropertyChangeListener
import java.io.Serializable
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent

class KafkaConsumerEditor(private val kafkaManager: KafkaDataManager,
                          private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private var consumerClient = KafkaConsumerClient(client = kafkaManager.client,
                                                   onStop = {
                                                     onStopConsume()
                                                   })
  val topics = kafkaManager.getTopics()

  private val startSpecificDate = DatePicker()
  private val limitSpecificDate = DatePicker()
  private val limitOffset = JBTextField()

  private val startOffset = JBTextField()
  private val startFromComboBox = ComboBox(ConsumerStartType.values()).apply {
    renderer = StartFromRenderer()
    item = ConsumerStartType.NOW
    addItemListener {
      updateStartWith()
    }
  }

  private val limitComboBox = ComboBox(ConsumerLimitType.values()).apply {
    renderer = LimitRenderer()
    item = ConsumerLimitType.NONE
    addItemListener {
      updateLimit()
    }
  }

  private val filterComboBox = ComboBox(ConsumerFilterType.values()).apply {
    renderer = FilterRenderer()
    item = ConsumerFilterType.NONE
    addItemListener {
      updateFilter()
    }
  }

  private val filterKeyField = JBTextField()
  private val filterValueField = JBTextField()
  private val filterHeadKeyField = JBTextField()
  private val filterHeadValueField = JBTextField()

  private val partitionField = JBTextField()

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

  private val clearButton = JButton(KafkaMessagesBundle.message("action.clear.output")).apply {
    addActionListener {
      outputModel.clear()
    }
  }

  private val filterPanel = MigPanel().apply {
    row("Filter key:", filterKeyField)
    row("Filter value:", filterValueField)
    row("Filter head key:", filterHeadKeyField)
    row("Filter head value:", filterHeadValueField)
  }

  private val mainComponent = createCenterPanel()

  init {
    Disposer.register(this, consumerClient)
    updateVisibility()
    updateLimit()
    updateStartWith()
    updateFilter()
  }

  private fun createCenterPanel() = OnePixelSplitter().apply {

    val leftPanel = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {

      row("Topics:", topicComboBox)

      title("Format")
      gapLeft = true
      row("Key:", keyComboBox)
      row("Value:", valueComboBox)

      title("Range and Filters")
      row("Start from:", startFromComboBox)
      add(startSpecificDate, UiUtil.growXSpanXWrap)
      add(startOffset, UiUtil.growXSpanXWrap)

      row("Limit:", limitComboBox)
      add(limitSpecificDate, UiUtil.growXSpanXWrap)
      add(limitOffset, UiUtil.growXSpanXWrap)

      row("Filter:", filterComboBox)
      add(filterPanel, UiUtil.growXSpanXWrap)

      title("Partitions")
      row("Partitions:", partitionField)
      gapLeft = false
      add(consumeButton, UiUtil.growXSpanXWrap)
      add(clearButton, UiUtil.growXSpanXWrap)
    }

    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE

    firstComponent = leftPanel
    secondComponent = JBScrollPane(outputList)

    proportion = 0.1f
  }

  private fun startConsume() {
    val topic = topicComboBox.item ?: error("Topic is not selected")
    val startWith = ConsumerEditorUtils.getStartWith(startFromComboBox.selectedItem as ConsumerStartType,
                                                     startOffset.text,
                                                     startSpecificDate.date)
    val partitions = ConsumerEditorUtils.parsePartitionsText(partitionField.text)
    val filter = getFilter()

    val consumerLimit = ConsumerLimit(limitComboBox.selectedItem as ConsumerLimitType, limitOffset.text, limitSpecificDate.date.time)
    val runConfig = RunConsumerConfig(topic = topic.name,
                                      partitions = partitions.ifEmpty { null },
                                      limit = consumerLimit,
                                      filter = filter,
                                      startWith = startWith)

    consumerClient.start(runConfig) { record ->
      outputModel.addElement(record)
    }
  }

  private fun getFilter() = ConsumerFilter(
    type = filterComboBox.item,
    filterKey = filterKeyField.text.ifBlank { null },
    filterValue = filterValueField.text.ifBlank { null },
    filterHeadKey = filterHeadKeyField.text.ifBlank { null },
    filterHeadValue = filterHeadValueField.text.ifBlank { null },
  )


  private fun updateVisibility() {
    val isEnabled = !consumerClient.isRunning()

    topicComboBox.isEnabled = isEnabled

    partitionField.isEnabled = isEnabled

    keyComboBox.isEnabled = isEnabled
    valueComboBox.isEnabled = isEnabled

    startFromComboBox.isEnabled = isEnabled
    startSpecificDate.isEnabled = isEnabled
    startOffset.isEnabled = isEnabled

    limitComboBox.isEnabled = isEnabled
    limitOffset.isEnabled = isEnabled
    limitSpecificDate.isEnabled = isEnabled

    filterComboBox.isEnabled = isEnabled
    filterKeyField.isEnabled = isEnabled
    filterValueField.isEnabled = isEnabled
    filterHeadKeyField.isEnabled = isEnabled
    filterHeadValueField.isEnabled = isEnabled
  }

  private fun updateStartWith() {
    startSpecificDate.isVisible = false
    startOffset.isVisible = false
    when (startFromComboBox.selectedItem) {
      ConsumerStartType.SPECIFIC_DATE -> startSpecificDate.isVisible = true
      ConsumerStartType.OFFSET, ConsumerStartType.LATEST_OFFSET_MINUS_X -> startOffset.isVisible = true
    }
  }

  private fun updateFilter() {
    val value = filterComboBox.selectedItem != ConsumerFilterType.NONE
    filterPanel.isVisible = value
    filterKeyField.isVisible = value
    filterValueField.isVisible = value
    filterHeadKeyField.isVisible = value
    filterHeadValueField.isVisible = value
  }

  private fun updateLimit() {
    limitSpecificDate.isVisible = false
    limitOffset.isVisible = false

    when (limitComboBox.selectedItem) {
      ConsumerLimitType.DATE -> limitSpecificDate.isVisible = true
      ConsumerLimitType.TOPIC_NUMBER_RECORDS,
      ConsumerLimitType.PARTITION_NUMBER_RECORDS,
      ConsumerLimitType.PARTITION_MAX_SIZE,
      ConsumerLimitType.TOPIC_MAX_SIZE -> limitOffset.isVisible = true
    }
  }

  private fun onStopConsume() {
    consumeButton.text = KafkaMessagesBundle.message("action.consume.start.title")
    updateVisibility()
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