package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.common.table.MaterialTable
import com.jetbrains.bigdatatools.common.table.MaterialTableUtils
import com.jetbrains.bigdatatools.common.table.extension.TableCellPreview
import com.jetbrains.bigdatatools.common.table.extension.TableFirstRowAdded
import com.jetbrains.bigdatatools.common.table.extension.TableLoadingDecorator
import com.jetbrains.bigdatatools.common.table.filters.TableFilterHeader
import com.jetbrains.bigdatatools.common.table.renderers.DateRenderer
import com.jetbrains.bigdatatools.common.ui.*
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.withPluginClassLoader
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.common.editor.SavePresetAction
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.consumer.client.KafkaConsumerClient
import com.jetbrains.bigdatatools.kafka.consumer.models.*
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.michaelbaranov.microba.calendar.DatePicker
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.*
import kotlin.math.max

class KafkaConsumerPanel(val project: Project, internal val kafkaManager: KafkaDataManager, private val file: VirtualFile) : Disposable {
  private var consumerClient: KafkaConsumerClient = KafkaConsumerClient(dataManager = kafkaManager,
                                                                        onStart = ::onStartConsume,
                                                                        onStop = ::onStopConsume)
  private val startSpecificDate = DatePicker()
  private val limitSpecificDate = DatePicker()
  private val limitOffset = JBTextField()

  private val startOffset = JBTextField()
  private val startConsumerGroup = KafkaEditorUtils.createConsumerGroups(this, kafkaManager)

  private val startFromComboBox = ComboBox(ConsumerStartType.values()).apply {
    prototypeDisplayValue = ConsumerStartType.TODAY
    renderer = CustomListCellRenderer<ConsumerStartType> { it.title }
    item = ConsumerStartType.NOW
    addItemListener {
      updateStartWith()
      storeToFile()
      getComponent().revalidate()
    }
  }

  private val limitComboBox = ComboBox(ConsumerLimitType.values()).apply {
    prototypeDisplayValue = ConsumerLimitType.DATE
    renderer = CustomListCellRenderer<ConsumerLimitType> { it.title }
    item = ConsumerLimitType.NONE
    addItemListener {
      updateLimit()
      storeToFile()
      getComponent().revalidate()
    }
  }

  private val filterComboBox = ComboBox(ConsumerFilterType.values()).apply {
    prototypeDisplayValue = ConsumerFilterType.NONE
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

  private val topicComboBox = KafkaEditorUtils.createTopicComboBox(this, kafkaManager).apply {
    prototypeDisplayValue = TopicInEditor("AverageName")
  }

  private val key = KafkaConsumerFieldComponent(this, isKey = true)
  private val value = KafkaConsumerFieldComponent(this, isKey = false)

  private val outputModel = ListTableModel(LinkedList<Result<ConsumerRecord<Any, Any>>>(),
                                           listOf("partition", "offset", "timestamp", "key", "value")) { data, index ->
    if (data.isFailure) {
      when (index) {
        3 -> "Error"
        4 -> data.exceptionOrNull()?.message ?: ""
        else -> null
      }
    }
    else {
      when (index) {
        0 -> data.getOrNull()?.partition()
        1 -> data.getOrNull()?.offset()
        2 -> data.getOrNull()?.let { Date(it.timestamp()) }
        3 -> KafkaEditorUtils.getValueAsString(key.typeComboBox.item, data.getOrNull()?.key())
        4 -> KafkaEditorUtils.getValueAsString(value.typeComboBox.item, data.getOrNull()?.value())
        else -> ""
      }
    }
  }.apply {
    columnClasses = listOf(Int::class.java, Long::class.java, Date::class.java, Object::class.java, Object::class.java)
  }

  private val outputTableDelegate = lazy {
    MaterialTable(outputModel, outputModel.columnModel).apply {
      tableHeader.border = BorderFactory.createEmptyBorder()
      outputModel.columnModel.columns.asIterator().forEach {
        if (it.headerValue == "timestamp") {
          it.cellRenderer = DateRenderer()
        }
      }

      TableFilterHeader(this)

      MaterialTableUtils.fitColumnsWidth(this)

      TableFirstRowAdded(this) {
        MaterialTableUtils.fitColumnsWidth(this)
      }

      setupTablePopupMenu(this)

      TableCellPreview.installOn(this, listOf("key", "value"))
    }
  }
  private val outputTable: MaterialTable by outputTableDelegate

  private val outputTablePanelDelegate = lazy {
    JPanel(BorderLayout()).apply {
      add(JBScrollPane(outputTable).apply {
        border = BorderFactory.createEmptyBorder()
      }, BorderLayout.CENTER)
      if (PropertiesComponent.getInstance().getBoolean(TABLE_STATS_ID, false)) {
        setSouthComponent(outputTableStatus.component)
      }
    }
  }
  private val outputTablePanel: JPanel by outputTablePanelDelegate

  private val outputTableStatusDelegate = lazy {
    ConsumerTableStats().apply {
      setModel(outputTable, outputModel)
    }
  }
  private val outputTableStatus: ConsumerTableStats by outputTableStatusDelegate

  private val kafkaConsumerSettingsDelegate = lazy { KafkaConsumerSettings() }
  private val kafkaConsumerSettings: KafkaConsumerSettings by kafkaConsumerSettingsDelegate

  private val advancedSettings = ActionLink(KafkaMessagesBundle.message("settings.advanced")) {
    kafkaConsumerSettings.show()
  }

  private val consumeButton: JButton = JButton(KafkaMessagesBundle.message("action.consume.start.title"), AllIcons.Actions.Execute).apply {
    addActionListener {
      executeOnPooledThread {
        try {
          if (consumerClient.isRunning()) {
            consumerClient.stop()
            tableLoadingDecorator?.let { Disposer.dispose(it) }
          }
          else {
            startConsume(kafkaManager.project)
          }
          updateVisibility()
          storeToFile()

          invalidate()
          repaint()
        }
        catch (t: Throwable) {
          @Suppress("DialogTitleCapitalization")
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

  internal val detailsDelegate: Lazy<ConsumerRecordDetails> = lazy {
    ConsumerRecordDetails(project, this).apply {
      keyType = key.typeComboBox.item
      valueType = value.typeComboBox.item
    }
  }
  internal val details: ConsumerRecordDetails by detailsDelegate

  private lateinit var startSpecificDateBlock: MigBlock
  private lateinit var startOffsetBlock: MigBlock
  private lateinit var startConsumerGroupBlock: MigBlock

  private lateinit var limitSpecificDateBlock: MigBlock
  private lateinit var limitOffsetBlock: MigBlock
  private lateinit var filterPanelBlock: MigBlock

  private val settingsPanelDelegate = lazy {
    val panel = MigPanel(LC().insets("10 10 0 0").fillX().hideMode(3)).apply {

      row(KafkaMessagesBundle.message("settings.label.topics"), topicComboBox)

      title(KafkaMessagesBundle.message("settings.title.format"))
      gapLeft = true
      row(KafkaMessagesBundle.message("settings.format.key"), key.typeComboBox)
      row(key.registryType)
      row(key.subjectComboBox)
      row(key.schemaIdField)
      row(key.schemaJsonField)

      row(KafkaMessagesBundle.message("settings.format.value"), value.typeComboBox)
      row(value.registryType)
      row(value.subjectComboBox)
      row(value.schemaIdField)
      row(value.schemaJsonField)


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

      spacing()

      row(advancedSettings)
    }

    val scroll = JBScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      minimumSize = Dimension(panel.minimumSize.width, minimumSize.height)
      border = BorderFactory.createEmptyBorder()
    }

    val bottomPanel = MigPanel(UiUtil.insets10FillXHidemode3).apply {
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

  private var tableLoadingDecorator: TableLoadingDecorator? = null

  init {
    Disposer.register(this, consumerClient)

    restoreFromFile()

    val clearButton = SimpleDumbAwareAction(KafkaMessagesBundle.message("action.clear.output"), AllIcons.Actions.GC) {
      outputModel.clear()
    }

    val tableStatusButton = object : DumbAwareToggleAction(KafkaMessagesBundle.message("action.table.stats"), null,
                                                           AllIcons.General.ShowInfos) {
      override fun isSelected(e: AnActionEvent) = outputTableStatusDelegate.isInitialized() && outputTableStatus.component.parent != null
      override fun getActionUpdateThread() = ActionUpdateThread.BGT
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) {
          outputTablePanel.setSouthComponent(outputTableStatus.component)
        }
        else {
          outputTablePanel.removeSouthComponent()
        }
        PropertiesComponent.getInstance().setValue(TABLE_STATS_ID, state)
        outputTablePanel.revalidate()
      }
    }

    val dataExpanded = PropertiesComponent.getInstance().getBoolean(DATA_SHOW_ID, true)
    resultsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.data"),
                                                    { outputTablePanel },
                                                    dataExpanded,
                                                    listOf(tableStatusButton, clearButton)
    ).apply {
      expandedServiceKey = DATA_SHOW_ID
      addChangeListener {
        resultsSplitter.proportion = if (this.expanded) 1f else 0.0001f
        resultsSplitter.setResizeEnabled(this.expanded)
      }
    }

    resultsSplitter.secondComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.details"), {
      details.component.apply {
        minimumSize = Dimension(max(details.component.minimumSize.width, 250), minimumSize.height)
      }
    }, PropertiesComponent.getInstance().getBoolean(DETAILS_SHOW_ID, false)).apply {
      expandedServiceKey = DETAILS_SHOW_ID
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
      expandedServiceKey = SETTINGS_SHOW_ID
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
      expandedServiceKey = PRESETS_SHOW_ID
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

  private fun startConsume(project: Project?) {
    val runConfig = getRunConfig()
    if (runConfig.topic.isNullOrBlank()) {
      invokeLater {
        Messages.showErrorDialog(kafkaManager.project,
                                 KafkaMessagesBundle.message("consumer.error.topic.empty"),
                                 KafkaMessagesBundle.message("consumer.error.topic.empty.title"))
      }
      return
    }

    try {
      if (outputTableDelegate.isInitialized()) {
        tableLoadingDecorator?.let { Disposer.dispose(it) }
        tableLoadingDecorator = TableLoadingDecorator.installOn(outputTable,
                                                                this@KafkaConsumerPanel,
                                                                KafkaMessagesBundle.message("consumer.table.awaiting"))
      }

      if (kafkaConsumerSettingsDelegate.isInitialized()) {
        val maxElementsCount = kafkaConsumerSettings.getSettings()[KafkaConsumerSettings.MAX_CONSUMER_RECORDS]
        maxElementsCount?.toIntOrNull()?.let {
          outputModel.maxElementsCount = it
        }
      }

      withPluginClassLoader {
        // Callbacks called in Kafka client threads. That's why, to properly update UI we calling invokeLater
        consumerClient.start(runConfig,
                             consume = {
                               invokeLater {
                                 outputModel.addElement(Result.success(it))
                                 if (outputTableStatusDelegate.isInitialized()) {
                                   outputTableStatus.addRecord(it)
                                 }
                               }
                             },
                             consumeError = {
                               invokeLater {
                                 outputModel.addElement(Result.failure(it))
                               }
                             })
      }
    }
    catch (t: Throwable) {
      onStopConsume()
      invokeLater {
        RfsNotificationUtils.showExceptionMessage(project, t, KafkaMessagesBundle.message("error.start.consumer"))
      }
      return
    }
  }

  private fun getRunConfig(): StorageConsumerConfig {
    val topicName = topicComboBox.item?.name ?: ""
    val startWith = ConsumerEditorUtils.getStartWith(startFromComboBox.item,
                                                     startOffset.text,
                                                     startSpecificDate.date,
                                                     startConsumerGroup.item?.consumerGroup)
    val filter = getFilter()

    val consumerLimit = ConsumerLimit(limitComboBox.item, limitOffset.text,
                                      if (limitComboBox.item == ConsumerLimitType.DATE) limitSpecificDate.date?.time else null)

    val (properties, settings) = if (kafkaConsumerSettingsDelegate.isInitialized()) {
      kafkaConsumerSettings.getProperties() to kafkaConsumerSettings.getSettings()
    }
    else {
      emptyMap<String, String>() to emptyMap()
    }

    return StorageConsumerConfig(topic = topicName,
                                 keyType = key.typeComboBox.item,
                                 valueType = value.typeComboBox.item,
                                 partitions = partitionField.text,
                                 limit = consumerLimit,
                                 filter = filter,
                                 startWith = startWith,
                                 properties = properties,
                                 settings = settings,

                                 keyRegistryType = key.registryType.item?.name ?: "",
                                 valueRegistryType = value.registryType.item?.name ?: "",

                                 keySchemaId = key.schemaIdField.text,
                                 valueSchemaId = value.schemaIdField.text,

                                 keySubject = key.subjectComboBox.item?.name ?: "",
                                 valueSubject = value.subjectComboBox.item?.name ?: "",

                                 keyCustomSchema = key.schemaJsonField.text,
                                 valueCustomSchema = value.schemaJsonField.text
    )
  }

  fun getComponent(): JComponent = presetsSplitter

  private fun setupTablePopupMenu(table: JTable) {
    val clearAction = SimpleDumbAwareAction(KafkaMessagesBundle.message("action.clear.output")) { outputModel.clear() }
    PopupHandler.installPopupMenu(table, DefaultActionGroup().apply {
      (ActionManager.getInstance().getAction("BdIde.TableEditor.PopupActionGroup") as? ActionGroup)?.let { addAll(it) }
      addSeparator()
      addAction(clearAction)
    }, "KafkaConsumerPanel")
  }

  private fun getFilter() = ConsumerFilter(
    type = filterComboBox.item,
    filterKey = filterKeyField.text.ifBlank { null },
    filterValue = filterValueField.text.ifBlank { null },
    filterHeadKey = filterHeadKeyField.text.ifBlank { null },
    filterHeadValue = filterHeadValueField.text.ifBlank { null },
  )

  internal fun updateVisibility() = invokeAndWaitIfNeeded {
    val isEnabled = !consumerClient.isRunning()

    topicComboBox.isEnabled = isEnabled

    partitionField.isEnabled = isEnabled

    key.updateIsEnabled(isEnabled)
    value.updateIsEnabled(isEnabled)

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

    advancedSettings.isEnabled = isEnabled
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

  private fun onStopConsume() = invokeLater {
    consumeButton.text = KafkaMessagesBundle.message("action.consume.start.title")
    consumeButton.icon = AllIcons.Actions.Execute
    updateVisibility()
  }

  private fun onStartConsume() = invokeLater {
    consumeButton.text = KafkaMessagesBundle.message("action.consume.stop.title")
    consumeButton.icon = AllIcons.Actions.Suspend
    updateVisibility()
  }

  private var isRestoring = false
  internal fun storeToFile() {
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

  private fun applyConfig(config: StorageConsumerConfig) {
    topicComboBox.item = TopicInEditor(config.getInnerTopic())

    key.load(config)
    value.load(config)

    val startWith = config.getStartsWith()
    startFromComboBox.item = startWith.type
    startOffset.text = startWith.offset?.toString() ?: ""
    startSpecificDate.date = startWith.time?.let { Date(it) }
    startConsumerGroup.item = kafkaManager.consumerGroupsModel.entries.firstOrNull { it.consumerGroup == startWith.consumerGroup }

    val limit = config.getLimit()
    limitComboBox.item = limit.type
    limitOffset.text = limit.value
    limitSpecificDate.date = limit.time?.let { Date(it) }

    val filter = config.getFilter()
    filterComboBox.item = filter.type
    filterKeyField.text = filter.filterKey
    filterValueField.text = filter.filterValue
    filterHeadKeyField.text = filter.filterHeadKey
    filterHeadValueField.text = filter.filterHeadValue

    partitionField.text = config.partitions

    kafkaConsumerSettings.applyConfig(config)
  }

  companion object {
    val STATE_KEY = Key<ConsumerEditorState>("STATE")

    // A number of string keys for PropertiesComponent.getInstance().getBoolean(**_**_ID, false)
    private const val DATA_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.data.show"
    private const val DETAILS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.details.show"
    private const val SETTINGS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.settings.show"
    private const val PRESETS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.presets.show"
    private const val TABLE_STATS_ID = "com.jetbrains.bigdatatools.kafka.consumer.table.stats.show"
  }
}