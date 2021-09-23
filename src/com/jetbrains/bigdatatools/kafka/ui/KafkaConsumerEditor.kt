package com.jetbrains.bigdatatools.kafka.ui


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
import com.jetbrains.bigdatatools.kafka.consumer.*
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.MigPanel
import com.michaelbaranov.microba.calendar.DatePicker
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.beans.PropertyChangeListener
import java.io.Serializable
import java.util.*
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

  private val limitComboBox = ComboBox(ConsumerLimit.values()).apply {
    renderer = LimitRenderer()
    item = ConsumerLimit.NONE
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

    val startOffset: Long? = when (startFromComboBox.selectedItem) {
      ConsumerStartType.OFFSET -> startOffset.text.ifBlank { null }?.toLongOrNull()
      ConsumerStartType.LATEST_OFFSET_MINUS_X -> startOffset.text.ifBlank { null }?.toLongOrNull()?.times(-1)
      ConsumerStartType.THE_BEGINNING -> 0
      else -> startOffset.text.ifBlank { null }?.toLongOrNull()
    }

    val calendar = Calendar.getInstance()
    calendar.time = Date()

    val startTime = when (startFromComboBox.selectedItem) {
      ConsumerStartType.NOW -> null
      ConsumerStartType.LAST_HOUR -> {
        calendar.add(Calendar.HOUR_OF_DAY, -1)
        calendar.time
      }
      ConsumerStartType.TODAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.time
      }
      ConsumerStartType.YESTERDAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.time
      }

      ConsumerStartType.SPECIFIC_DATE -> startSpecificDate.date
      else -> null
    }

    val partitionsStrings = partitionField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val partitions = partitionsStrings.flatMap { p ->
      if (!p.contains("-"))
        listOfNotNull(p.toIntOrNull())
      else {
        val range = p.split("-").map { it.trim() }
        val start = range.first().trim().toIntOrNull() ?: return@flatMap emptyList<Int>()
        val end = range.last().trim().toIntOrNull() ?: return@flatMap emptyList<Int>()
        start..end
      }
    }

    val filter = ConsumerFilter(
      type = filterComboBox.item,
      filterKey = filterKeyField.text.ifBlank { null },
      filterValue = filterValueField.text.ifBlank { null },
      filterHeadKey = filterHeadKeyField.text.ifBlank { null },
      filterHeadValue = filterHeadValueField.text.ifBlank { null },
    )

    val startWith = ConsumerStartWith(offset = startOffset, time = startTime?.time)
    consumerClient.start(topic = topic.name,
                         partitionFilter = partitions.ifEmpty { null },
                         topicLimitCount = getLimitTopicCount(),
                         partitionLimitCount = getLimitPartitionCount(),
                         limitTime = getLimitTime(),
                         topicLimitSize = getLimitTopicSize(),
                         partitionLimitSize = getLimitPartitionsSize(),
                         filter = filter,
                         startWith = startWith)
    { record ->
      outputModel.addElement(record)
    }
  }

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