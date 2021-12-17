package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.common.editor.SavePresetAction
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.consumer.client.KafkaConsumerClient
import com.jetbrains.bigdatatools.kafka.consumer.models.*
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.table.ClipboardUtils
import com.jetbrains.bigdatatools.table.MaterialTable
import com.jetbrains.bigdatatools.table.MaterialTableUtils
import com.jetbrains.bigdatatools.table.TableResizeController
import com.jetbrains.bigdatatools.table.extension.TableFirstRowAdded
import com.jetbrains.bigdatatools.table.filters.TableFilterHeader
import com.jetbrains.bigdatatools.table.renderers.DateRenderer
import com.jetbrains.bigdatatools.ui.*
import com.jetbrains.bigdatatools.util.MessagesBundle
import com.jetbrains.bigdatatools.util.executeOnPooledThread
import com.michaelbaranov.microba.calendar.DatePicker
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.*
import kotlin.math.max

class KafkaConsumerPanel(private val kafkaManager: KafkaDataManager,
                         private val file: VirtualFile) : Disposable {
  private var consumerClient = KafkaConsumerClient(client = kafkaManager.client,
                                                   onStart = ::onStartConsume,
                                                   onStop = ::onStopConsume)
  private val startSpecificDate = DatePicker()
  private val limitSpecificDate = DatePicker()
  private val limitOffset = JBTextField()

  private val startOffset = JBTextField()
  private val startConsumerGroup = KafkaEditorUtils.createConsumerGroups(this, kafkaManager)
  private val startFromComboBox = ComboBox(ConsumerStartType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerStartType> { it.title }
    item = ConsumerStartType.NOW
    addItemListener {
      updateStartWith()
      storeToFile()
      getComponent().revalidate()
    }
  }

  private val limitComboBox = ComboBox(ConsumerLimitType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerLimitType> { it.title }
    item = ConsumerLimitType.NONE
    addItemListener {
      updateLimit()
      storeToFile()
      getComponent().revalidate()
    }
  }

  private val filterComboBox = ComboBox(ConsumerFilterType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerFilterType> { it.title }
    item = ConsumerFilterType.NONE
    addItemListener {
      updateFilter()
      storeToFile()
      getComponent().revalidate()
    }
  }

  private val filterKeyField = JBTextField()
  private val filterValueField = JBTextField()
  private val filterHeadKeyField = JBTextField()
  private val filterHeadValueField = JBTextField()

  private val partitionField = JBTextField()

  private val topicComboBox = KafkaEditorUtils.createTopicComboBox(this, kafkaManager)

  private val keyComboBox = ComboBox(FieldType.values()).apply {
    renderer = CustomListCellRenderer<FieldType> { it.title }
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
      storeToFile()
    }
  }

  private val valueComboBox = ComboBox(FieldType.values()).apply {
    renderer = CustomListCellRenderer<FieldType> { it.title }
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
      storeToFile()
    }
  }

  private val outputModel = ListTableModel(ArrayList<Result<ConsumerRecord<Any, Any>>>(),
                                           listOf("partition", "offset", "timestamp", "value")) { data, index ->

    if (data.isFailure) {
      when (index) {
        0 -> ""
        1 -> ""
        2 -> "Error"
        3 -> data.exceptionOrNull()?.message ?: ""
        else -> ""
      }
    }
    else {
      when (index) {
        0 -> data.getOrNull()?.partition() ?: ""
        1 -> data.getOrNull()?.offset() ?: ""
        2 -> data.getOrNull()?.timestamp() ?: ""
        3 -> data.getOrNull()?.value() ?: ""
        else -> ""
      }
    }
  }

  private val outputTableDelegate = lazy {
    MaterialTable(outputModel, outputModel.columnModel).apply {
      val resizeController = TableResizeController(this)
      tableHeader.border = JBUI.Borders.empty()
      outputModel.columnModel.columns.asIterator().forEach {
        if (it.headerValue == "timestamp") {
          it.cellRenderer = DateRenderer()
        }
      }
      TableFilterHeader(this)
      TableFirstRowAdded(this) {
        MaterialTableUtils.fitColumnsWidth(this)
        resizeController.componentResized()
      }

      setupTablePopupMenu(this)
    }
  }
  private val outputTable: MaterialTable by outputTableDelegate

  private val consumeButton = JButton(KafkaMessagesBundle.message("action.consume.start.title"), AllIcons.Actions.Execute).apply {
    addActionListener {
      executeOnPooledThread {
        try {
          if (consumerClient.isRunning()) {
            consumerClient.stop()
          }
          else {
            startConsume()
          }
          updateVisibility()
          storeToFile()

          invalidate()
          repaint()
        }
        catch (t: Throwable) {
          RfsNotificationUtils.notifyException(t, KafkaMessagesBundle.message("error.start.consumer"))
        }
      }
    }
  }

  private val filterPanel = MigPanel().apply {
    row(KafkaMessagesBundle.message("label.filter.key"), filterKeyField)
    row(KafkaMessagesBundle.message("label.filter.value"), filterValueField)
    row(KafkaMessagesBundle.message("label.filter.head.key"), filterHeadKeyField)
    row(KafkaMessagesBundle.message("label.filter.head.value"), filterHeadValueField)
  }

  private val detailsDelegate = lazy { ConsumerRecordDetails() }
  private val details: ConsumerRecordDetails by detailsDelegate

  private lateinit var startSpecificDateBlock: MigBlock
  private lateinit var startOffsetBlock: MigBlock
  private lateinit var startConsumerGroupBlock: MigBlock

  private lateinit var limitSpecificDateBlock: MigBlock
  private lateinit var limitOffsetBlock: MigBlock
  private lateinit var filterPanelBlock: MigBlock

  private val settingsPanelDelegate = lazy {
    val panel = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {

      row(KafkaMessagesBundle.message("settings.label.topics"), topicComboBox)

      title(KafkaMessagesBundle.message("settings.title.format"))
      gapLeft = true
      row(KafkaMessagesBundle.message("settings.format.key"), keyComboBox)
      row(KafkaMessagesBundle.message("settings.format.value"), valueComboBox)

      title(KafkaMessagesBundle.message("settings.title.range.filters"))
      row(KafkaMessagesBundle.message("settings.filters.from"), startFromComboBox)

      startSpecificDateBlock = MigBlock(this).apply {
        row(EmptyCell(), startSpecificDate)
      }
      startOffsetBlock = MigBlock(this).apply {
        row(EmptyCell(), startOffset)
      }
      startConsumerGroupBlock = MigBlock(this).apply {
        row(EmptyCell(), startConsumerGroup)
      }

      row(KafkaMessagesBundle.message("settings.filters.limit"), limitComboBox)
      limitSpecificDateBlock = MigBlock(this).apply {
        row(EmptyCell(), limitSpecificDate)
      }
      limitOffsetBlock = MigBlock(this).apply {
        row(EmptyCell(), limitOffset)
      }

      row(KafkaMessagesBundle.message("settings.filter"), filterComboBox)
      filterPanelBlock = MigBlock(this).apply {
        row(EmptyCell(), filterPanel)
      }

      title(KafkaMessagesBundle.message("settings.title.partitions"))
      row(KafkaMessagesBundle.message("settings.partitions"), partitionField)

      gapLeft = false
    }

    val scroll = JBScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      minimumSize = Dimension(panel.minimumSize.width, minimumSize.height)
      border = BorderFactory.createEmptyBorder()
    }

    val bottomPanel = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
      add(consumeButton)
    }

    JPanel(BorderLayout()).apply {
      add(scroll, BorderLayout.CENTER)
      add(bottomPanel, BorderLayout.SOUTH)
    }
  }

  private val settingsPanel: JPanel by settingsPanelDelegate

  private val presetsDelegate = lazy {
    val presets = ConsumerPresets()
    Disposer.register(this, presets)
    presets.onApply = { applyConfig(it) }
    presets
  }
  private val presets: ConsumerPresets by presetsDelegate

  private val resultsSplitter = OnePixelSplitter().apply {
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE
    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_SECOND_SIZE
  }

  private val settingsSplitter = OnePixelSplitter().apply {
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE
    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
    secondComponent = resultsSplitter
    proportion = 0.0001f
  }

  private val presetsSplitter = OnePixelSplitter().apply {
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE
    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
    secondComponent = settingsSplitter
    proportion = 0.0001f
  }

  init {
    Disposer.register(this, consumerClient)

    restoreFromFile()

    val clearButton = object : DumbAwareAction(KafkaMessagesBundle.message("action.clear.output"), null, AllIcons.Actions.GC) {
      override fun actionPerformed(e: AnActionEvent) {
        outputModel.clear()
      }
    }

    val dataExpanded = PropertiesComponent.getInstance().getBoolean(DATA_SHOW_ID, true)
    resultsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.data"),
                                                    { JBScrollPane(outputTable).apply { border = BorderFactory.createEmptyBorder() } },
                                                    dataExpanded,
                                                    listOf(clearButton)
    ).apply {
      addChangeListener {
        resultsSplitter.proportion = if (this.expanded) 1f else 0.0001f
        resultsSplitter.setResizeEnabled(this.expanded)
      }
    }
    resultsSplitter.secondComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.details"), {
      JBScrollPane(details.component, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
        minimumSize = Dimension(max(details.component.minimumSize.width, 250), minimumSize.height)
        border = BorderFactory.createEmptyBorder()
      }
    }, PropertiesComponent.getInstance().getBoolean(DETAILS_SHOW_ID, false)).apply {
      addChangeListener {
        resultsSplitter.proportion = 1f
        if (this.expanded) {
          updateDetails()
        }
      }
    }
    resultsSplitter.proportion = if (dataExpanded) 1f else 0.0001f
    resultsSplitter.setResizeEnabled(dataExpanded)

    val settingsExpanded = PropertiesComponent.getInstance().getBoolean(SETTINGS_SHOW_ID, true)
    settingsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.settings"), { settingsPanel },
                                                     settingsExpanded,
                                                     listOf(SavePresetAction(KafkaConfigStorage.instance.consumerConfig) { getRunConfig() })
    ).apply {
      addChangeListener {
        settingsSplitter.proportion = 0.0001f
        settingsSplitter.setResizeEnabled(this.expanded)
      }
    }
    settingsSplitter.setResizeEnabled(settingsExpanded)

    val presetsExpanded = PropertiesComponent.getInstance().getBoolean(PRESETS_SHOW_ID, false)
    presetsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.presets"), {
      presets.component.apply {
        minimumSize = Dimension(max(minimumSize.width, 290), minimumSize.height)
      }
    }, presetsExpanded).apply {
      addChangeListener {
        presetsSplitter.proportion = 0.0001f
        presetsSplitter.setResizeEnabled(this.expanded)
      }
    }
    presetsSplitter.setResizeEnabled(presetsExpanded)

    updateVisibility()
    updateLimit()
    updateStartWith()
    updateFilter()

    storeToFile()

    outputTable.selectionModel.addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        updateDetails()
      }
    }
  }

  private fun updateDetails() {
    if (detailsDelegate.isInitialized()) {
      details.record = if (outputTable.selectedRow == -1) null
      else outputModel.getValueAt(outputTable.convertRowIndexToModel(outputTable.selectedRow))?.getOrNull()
    }
  }

  override fun dispose() {
    storeToFile()
  }

  private fun startConsume() {
    val runConfig = getRunConfig()
    if (runConfig.topic.isBlank()) {
      invokeLater {
        Messages.showErrorDialog(kafkaManager.project,
                                 KafkaMessagesBundle.message("consumer.error.topic.empty"),
                                 KafkaMessagesBundle.message("consumer.error.topic.empty.title"))
      }
      return
    }
    consumerClient.start(runConfig,
                         consume = { invokeLater { outputModel.addElement(Result.success(it)) } },
                         consumeError = { invokeLater { outputModel.addElement(Result.failure(it)) } })
  }

  private fun getRunConfig(): RunConsumerConfig {
    val topicName = topicComboBox.item?.name ?: ""
    val startWith = ConsumerEditorUtils.getStartWith(startFromComboBox.item,
                                                     startOffset.text,
                                                     startSpecificDate.date,
                                                     startConsumerGroup.item?.consumerGroup)
    val filter = getFilter()

    val consumerLimit = ConsumerLimit(limitComboBox.item, limitOffset.text,
                                      if (limitComboBox.item == ConsumerLimitType.DATE) limitSpecificDate.date?.time else null)

    return RunConsumerConfig(topic = topicName,
                             keyType = keyComboBox.item,
                             valueType = valueComboBox.item,
                             partitions = partitionField.text,
                             limit = consumerLimit,
                             filter = filter,
                             startWith = startWith)
  }

  fun getComponent(): JComponent = presetsSplitter

  private fun setupTablePopupMenu(table: JTable) {
    val copyAll = JMenuItem(MessagesBundle.message("table.copyAll"))
    copyAll.addActionListener { ClipboardUtils.copyAllToClipboard(table) }

    val copySelected = JMenuItem(MessagesBundle.message("table.copySelected"))
    copySelected.addActionListener { ClipboardUtils.copySelectedToClipboard(table) }

    val clear = JMenuItem(KafkaMessagesBundle.message("action.clear.output"))
    clear.addActionListener { outputModel.clear() }

    table.componentPopupMenu = JPopupMenu().apply {
      add(copyAll)
      add(copySelected)
      add(clear)
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
    startConsumerGroup.isEnabled = isEnabled
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
    startSpecificDateBlock.isVisible = startFromComboBox.selectedItem == ConsumerStartType.SPECIFIC_DATE
    startOffsetBlock.isVisible = startFromComboBox.selectedItem == ConsumerStartType.OFFSET ||
                                 startFromComboBox.selectedItem == ConsumerStartType.LATEST_OFFSET_MINUS_X
    startConsumerGroupBlock.isVisible = startFromComboBox.selectedItem == ConsumerStartType.CONSUMER_GROUP
  }

  private fun updateFilter() {
    filterPanelBlock.isVisible = filterComboBox.selectedItem != ConsumerFilterType.NONE
  }

  private fun updateLimit() {
    limitSpecificDateBlock.isVisible = false
    limitOffsetBlock.isVisible = false

    when (limitComboBox.selectedItem) {
      ConsumerLimitType.DATE -> limitSpecificDateBlock.isVisible = true
      ConsumerLimitType.TOPIC_NUMBER_RECORDS,
      ConsumerLimitType.PARTITION_NUMBER_RECORDS,
      ConsumerLimitType.PARTITION_MAX_SIZE,
      ConsumerLimitType.TOPIC_MAX_SIZE -> limitOffsetBlock.isVisible = true
    }
  }

  private fun onStopConsume() {
    consumeButton.text = KafkaMessagesBundle.message("action.consume.start.title")
    consumeButton.icon = AllIcons.Actions.Execute
    updateVisibility()
  }

  private fun onStartConsume() {
    consumeButton.text = KafkaMessagesBundle.message("action.consume.stop.title")
    consumeButton.icon = AllIcons.Actions.Suspend
    updateVisibility()
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
      applyConfig(state.config)
    }
    finally {
      isRestoring = false
    }
  }

  private fun applyConfig(config: RunConsumerConfig) {
    topicComboBox.item = TopicInEditor(config.topic)
    keyComboBox.item = config.keyType
    valueComboBox.item = config.valueType

    startFromComboBox.item = config.startWith.type
    startOffset.text = config.startWith.offset?.toString() ?: ""
    startSpecificDate.date = config.startWith.time?.let { Date(it) }
    startConsumerGroup.item = kafkaManager.consumerGroupsModel.entries.firstOrNull { it.consumerGroup == config.startWith.consumerGroup }

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

  companion object {
    val STATE_KEY = Key<ConsumerEditorState>("STATE")

    private const val DATA_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.data.show"
    private const val DETAILS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.details.show"
    private const val SETTINGS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.settings.show"
    private const val PRESETS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.presets.show"
  }
}