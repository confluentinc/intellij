package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.renders.FieldTypeRenderer
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.consumer.client.KafkaConsumerClient
import com.jetbrains.bigdatatools.kafka.consumer.models.*
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.table.MaterialTable
import com.jetbrains.bigdatatools.table.TableResizeController
import com.jetbrains.bigdatatools.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.ui.ExpansionPanel
import com.jetbrains.bigdatatools.ui.MigPanel
import com.michaelbaranov.microba.calendar.DatePicker
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.beans.PropertyChangeListener
import java.io.Serializable
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent

class KafkaConsumerEditor(val project: Project,
                          kafkaManager: KafkaDataManager,
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
    renderer = CustomListCellRenderer<ConsumerStartType> { it.title }
    item = ConsumerStartType.NOW
    addItemListener {
      updateStartWith()
      storeToFile()
    }
  }

  private val limitComboBox = ComboBox(ConsumerLimitType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerLimitType> { it.title }
    item = ConsumerLimitType.NONE
    addItemListener {
      updateLimit()
      storeToFile()
    }
  }

  private val filterComboBox = ComboBox(ConsumerFilterType.values()).apply {
    renderer = CustomListCellRenderer<ConsumerFilterType> { it.title }
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

  private val outputModel = ConsumerTableModel(ArrayList<Result<ConsumerRecord<Serializable, Serializable>>>(),
                                               listOf("partition", "offset", "timestamp", "value")) { data, index ->
    when (index) {
      0 -> data.getOrNull()?.partition() ?: ""
      1 -> data.getOrNull()?.offset() ?: ""
      2 -> data.getOrNull()?.timestamp() ?: ""
      3 -> data.getOrNull()?.value() ?: ""
      else -> ""
    }
  }

  private val outputTableDelegate = lazy { MaterialTable(outputModel, outputModel.columnModel).apply {
    TableResizeController.installOn(this)
    tableHeader.border = JBUI.Borders.empty()
  } }
  private val outputTable: MaterialTable by outputTableDelegate

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

  private val savePresetButton = JButton(KafkaMessagesBundle.message("action.save.preset")).apply {
    addActionListener {
      KafkaConfigStorage.instance.addConsumerConfig(getRunConfig())
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

  private val settingsPanelDelegate = lazy {
    MigPanel(LC().insets("10").fillX().hideMode(3)).apply {

      row(KafkaMessagesBundle.message("settings.label.topics"), topicComboBox)

      title(KafkaMessagesBundle.message("settings.title.format"))
      gapLeft = true
      row(KafkaMessagesBundle.message("settings.format.key"), keyComboBox)
      row(KafkaMessagesBundle.message("settings.format.value"), valueComboBox)

      title(KafkaMessagesBundle.message("settings.title.range.filters"))
      row(KafkaMessagesBundle.message("settings.filters.from"), startFromComboBox)
      add(startSpecificDate, UiUtil.growXSpanXWrap)
      add(startOffset, UiUtil.growXSpanXWrap)

      row(KafkaMessagesBundle.message("settings.filters.limit"), limitComboBox)
      add(limitSpecificDate, UiUtil.growXSpanXWrap)
      add(limitOffset, UiUtil.growXSpanXWrap)

      row(KafkaMessagesBundle.message("settings.filter"), filterComboBox)
      add(filterPanel, UiUtil.growXSpanXWrap)

      title(KafkaMessagesBundle.message("settings.title.partitions"))
      row(KafkaMessagesBundle.message("settings.partitions"), partitionField)
      gapLeft = false
      add(consumeButton, UiUtil.growXSpanXWrap)
      add(clearButton, UiUtil.growXSpanXWrap)
      add(savePresetButton, UiUtil.growXSpanXWrap)
    }
  }
  private val settingsPanel: MigPanel by settingsPanelDelegate

  private val presetsDelegate = lazy { ConsumerPresets() }
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

  private val mainComponent = createCenterPanel()

  init {
    Disposer.register(this, consumerClient)

    file.getUserData(STATE_KEY)?.let { restoreFromFile(it) }

    resultsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.data"), {
      JBScrollPane(outputTable).apply {
        border = BorderFactory.createEmptyBorder()
      }
    }, PropertiesComponent.getInstance().getBoolean(DATA_SHOW_ID, true)).apply {
      addChangeListener {
        resultsSplitter.proportion = if (this.expanded) 1f else 0.0001f
      }
    }
    resultsSplitter.secondComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.details"), { details.component },
                                                     PropertiesComponent.getInstance().getBoolean(DETAILS_SHOW_ID, false)).apply {
      addChangeListener {
        resultsSplitter.proportion = 1f
      }
    }

    resultsSplitter.proportion = if (PropertiesComponent.getInstance().getBoolean(DATA_SHOW_ID, true)) 1f else 0.0001f

    settingsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.settings"), { settingsPanel },
                                                     PropertiesComponent.getInstance().getBoolean(SETTINGS_SHOW_ID, true)).apply {
      addChangeListener {
        settingsSplitter.proportion = 0.0001f
      }
    }

    presetsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.presets"), { presets.component },
                                                    PropertiesComponent.getInstance().getBoolean(PRESETS_SHOW_ID, false)).apply {
      addChangeListener {
        presetsSplitter.proportion = 0.0001f
      }
    }

    updateVisibility()
    updateLimit()
    updateStartWith()
    updateFilter()

    storeToFile()

    outputTable.selectionModel.addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        if (detailsDelegate.isInitialized()) {
          details.record = outputModel.getValueAt(outputTable.selectedRow)?.getOrNull()
        }
      }
    }
  }

  override fun dispose() {
    storeToFile()
  }

  private fun startConsume() {
    val runConfig = getRunConfig()
    if (runConfig.topic.isBlank()) {
      Messages.showErrorDialog(project, "Topic is empty", "Consumer error")
      return
    }
    consumerClient.start(runConfig,
                         consume = { record ->
                           val success: Result<ConsumerRecord<Serializable, Serializable>> = Result.success(record)
                           outputModel.addElement(success)
                         },
                         consumeError = {
                           outputModel.addElement(Result.failure(it))
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

  private fun createCenterPanel(): JComponent {
    //
    //val stripe = JPanel(null).apply {
    //  layout = BoxLayout(this, BoxLayout.Y_AXIS)
    //  add(VerticalButton(KafkaMessagesBundle.message("toggle.presets"), AllIcons.Toolwindows.ToolWindowFavorites, false).apply {
    //    isSelected = showPresets
    //    addActionListener { showPresets = isSelected }
    //  })
    //  add(VerticalButton(KafkaMessagesBundle.message("toggle.settings"), AllIcons.General.Settings, false).apply {
    //    isSelected = showSettings
    //    addActionListener { showSettings = isSelected }
    //  })
    //  add(VerticalButton(KafkaMessagesBundle.message("toggle.details"), AllIcons.Actions.SplitVertically, false).apply {
    //    isSelected = showDetails
    //    addActionListener { showDetails = isSelected }
    //  })
    //
    //  border = IdeBorderFactory.createBorder(SideBorder.RIGHT)
    //}
    //
    //return JPanel(BorderLayout()).apply {
    //  add(presetsSplitter, BorderLayout.CENTER)
    //  add(stripe, BorderLayout.LINE_START)
    //}

    //return MigPanel().apply {
    //  add(ExpansionPanel(KafkaMessagesBundle.message("toggle.presets"), { presets.component },
    //                     PropertiesComponent.getInstance().getBoolean(PRESETS_SHOW_ID, false)), CC().growY().pushY())
    //  add(ComponentDivider(), CC().growY().pushY())
    //  add(ExpansionPanel(KafkaMessagesBundle.message("toggle.settings"), { settingsPanel },
    //                     PropertiesComponent.getInstance().getBoolean(SETTINGS_SHOW_ID, true)), CC().growY().pushY())
    //  add(ComponentDivider(), CC().growY().pushY())
    //  add(ExpansionPanel(KafkaMessagesBundle.message("toggle.data"), { outputList },
    //                     PropertiesComponent.getInstance().getBoolean(DATA_SHOW_ID, true)), CC().grow().push())
    //  add(ComponentDivider(), CC().growY().pushY())
    //  add(ExpansionPanel(KafkaMessagesBundle.message("toggle.details"), { details.component },
    //                     PropertiesComponent.getInstance().getBoolean(DETAILS_SHOW_ID, false)), CC().growY().pushY())
    //}


    return presetsSplitter
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

  private fun restoreFromFile(state: ConsumerEditorState) {
    try {
      isRestoring = true

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

    private const val DATA_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.data.show"
    private const val DETAILS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.details.show"
    private const val SETTINGS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.settings.show"
    private const val PRESETS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.presets.show"
  }
}