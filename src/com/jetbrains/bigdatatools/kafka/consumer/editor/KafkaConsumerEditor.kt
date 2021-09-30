package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.renders.FieldTypeRenderer
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.consumer.client.KafkaConsumerClient
import com.jetbrains.bigdatatools.kafka.consumer.models.*
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.editor.renders.ConsumerOutputRender
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.ui.MigPanel
import com.jetbrains.bigdatatools.util.toPresentableText
import com.michaelbaranov.microba.calendar.DatePicker
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.beans.PropertyChangeListener
import java.io.Serializable
import java.util.*
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent

class KafkaConsumerEditor(kafkaManager: KafkaDataManager,
                          private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private var consumerClient = KafkaConsumerClient(client = kafkaManager.client,
                                                   onStop = {
                                                     onStopConsume()
                                                   })
  private val startSpecificDate = DatePicker()
  private val limitSpecificDate = DatePicker()
  private val limitOffset = JBTextField()

  private val startOffset = JBTextField()
  private val startFromComboBox = ComboBox(ConsumerStartType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerStartType> { value -> value.name.toLowerCase() }
    item = ConsumerStartType.NOW
    addItemListener {
      updateStartWith()
      storeToFile()
    }
  }

  private val limitComboBox = ComboBox(ConsumerLimitType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerLimitType> { value -> value.name.toLowerCase() }
    item = ConsumerLimitType.NONE
    addItemListener {
      updateLimit()
      storeToFile()
    }
  }

  private val filterComboBox = ComboBox(ConsumerFilterType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerFilterType> { value -> value.name.toLowerCase() }
    item = ConsumerFilterType.NONE
    addItemListener {
      updateFilter()
      storeToFile()
    }
  }

  private val filterKeyField = JBTextField()
  private val filterValueField = JBTextField()
  private val filterHeadKeyField = JBTextField()
  private val filterHeadValueField = JBTextField()

  private val partitionField = JBTextField()

  private val topicComboBox = KafkaEditorUtils.createTopicComboBox(this, kafkaManager)

  private val keyComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
      storeToFile()
    }
  }

  private val valueComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
      storeToFile()
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
      storeToFile()

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

    restoreFromFile()
    updateVisibility()
    updateLimit()
    updateStartWith()
    updateFilter()

    storeToFile()
  }

  override fun dispose() {
    storeToFile()
  }

  private fun startConsume() {
    val runConfig = getRunConfig()
    consumerClient.start(runConfig,
                         consume = { record ->
                           outputModel.addElement(record)
                         },
                         consumeError = {
                           invokeLater {
                             Messages.showErrorDialog(it.toPresentableText(), "Produce error")
                           }
                         })
  }

  private fun getRunConfig(): RunConsumerConfig {
    val topicName = topicComboBox.item?.name ?: ""
    val startWith = ConsumerEditorUtils.getStartWith(startFromComboBox.selectedItem as ConsumerStartType,
                                                     startOffset.text,
                                                     startSpecificDate.date)
    val filter = getFilter()

    val consumerLimit = ConsumerLimit(limitComboBox.selectedItem as ConsumerLimitType, limitOffset.text, limitSpecificDate.date?.time)

    return RunConsumerConfig(topic = topicName,
                             keyType = keyComboBox.item as FieldType,
                             valueType = valueComboBox.item as FieldType,
                             partitions = partitionField.text,
                             limit = consumerLimit,
                             filter = filter,
                             startWith = startWith)
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

    storeToFile()
  }

  var isRestoring = false
  private fun storeToFile() {
    if (isRestoring)
      return
    file.putUserData(STATE_KEY, ConsumerEditorState(outputModel.elements().toList(), getRunConfig()))
  }

  private fun restoreFromFile() {
    try {
      isRestoring = true

      val state = file.getUserData(STATE_KEY) ?: return
      outputModel.clear()
      state.output.forEach {
        outputModel.addElement(it)
      }
      val config = state.config

      topicComboBox.item = TopicInEditor(config.topic)
      keyComboBox.item = config.keyType
      valueComboBox.item = config.valueType

      limitComboBox.item = config.limit.type
      limitOffset.text = config.limit.value
      limitSpecificDate.date = config.limit.time?.let { Date(it) }

      filterComboBox.item = config.filter.type
      filterKeyField.text = config.filter.filterKey
      filterValueField.text = config.filter.filterValue
      filterHeadKeyField.text = config.filter.filterHeadKey
      filterHeadValueField.text = config.filter.filterHeadValue

      partitionField.text = config.partitions
    }
    finally {
      isRestoring = false
    }
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

  companion object {
    val STATE_KEY = Key<ConsumerEditorState>("STATE")
  }
}