package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.getValidationInfo
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.ExpansionPanel
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaProducerConsumerProgressComponent
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.editor.SavePresetAction
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaRecordsOutput
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerEditorState
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerFlowParams
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.beans.PropertyChangeListener
import javax.swing.*
import kotlin.math.max

class KafkaProducerEditor(val project: Project,
                          internal val kafkaManager: KafkaDataManager,
                          private val file: VirtualFile,
                          topic: String?) : FileEditor, UserDataHolderBase() {
  private var isRestoring = false

  private val output = KafkaRecordsOutput(project, isProducer = true).also { Disposer.register(this, it) }

  private val flowController = KafkaFlowController()
  private val progress = KafkaProducerConsumerProgressComponent()

  private val producerClient = kafkaManager.client.createProducerClient().also {
    Disposer.register(this, Disposable {
      it.isRunning.set(false)
    })
  }
  val topics = kafkaManager.getTopics()

  private val propertiesComponent = PropertiesTable("app.name=IntellijKafkaPlugin")

  val topicComboBox = KafkaEditorUtils.createTopicComboBox(this, kafkaManager)

  private lateinit var acksComboBox: SegmentedButton<AcksType>

  private val idempotenceCheckBox = CheckBox(KafkaMessagesBundle.message("producer.idempotence.label")).apply {
    addChangeListener {
      acksComboBox.enabled(!isSelected)
    }
  }

  private val compressionComboBox = ComboBox(RecordCompression.values()).apply {
    renderer = CustomListCellRenderer<RecordCompression> { StringUtil.wordsToBeginFromUpperCase(it.name.lowercase()) }
    selectedIndex = 0
  }

  private val keyFieldComponent = KafkaProducerFieldComponent(this, isKey = true).also { Disposer.register(this, it) }
  private val valueFieldComponent = KafkaProducerFieldComponent(this, isKey = false).also { Disposer.register(this, it) }

  private val forcePartitionField = IntegerField().apply {
    isCanBeEmpty = true
    defaultValue = -1
    emptyText.text = KafkaMessagesBundle.message("producer.forcePartition.emptytext")
  }


  private val presetsDelegate = lazy {
    val presets = ProducerPresets()
    Disposer.register(this, presets)
    presets.onApply = { applyConfig(it) }
    presets
  }

  private val presets: ProducerPresets by presetsDelegate

  private val produceButton = JButton(KafkaMessagesBundle.message("kafka.producer.action.produce.title"), AllIcons.Actions.Execute).also {
    it.addActionListener {
      if (producerClient.isRunning())
        stopProduce()
      else
        startProduce()
    }
  }


  private val settingsPanelDelegate = lazy {
    val panel = panel {
      row(KafkaMessagesBundle.message("producer.topics")) { cell(topicComboBox).align(AlignX.FILL).resizableColumn() }

      keyFieldComponent.createComponent(this)
      valueFieldComponent.createComponent(this)

      collapsibleGroup(KafkaMessagesBundle.message("producer.title.headers")) {
        row { cell(propertiesComponent.getComponent()).align(AlignX.FILL).resizableColumn() }
      }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)


      flowController.getComponent(this)

      collapsibleGroup(KafkaMessagesBundle.message("producer.title.options")) {
        row(KafkaMessagesBundle.message("producer.forcePartition")) {
          cell(forcePartitionField).align(AlignX.FILL).resizableColumn()
        }
        row(KafkaMessagesBundle.message("producer.compression")) {
          cell(compressionComboBox).align(AlignX.FILL).resizableColumn()
        }
        row {
          cell(idempotenceCheckBox).align(AlignX.FILL).resizableColumn().comment(
            KafkaMessagesBundle.message("producer.idempotence.comment"))
        }
        row(KafkaMessagesBundle.message("producer.asks")) {
          acksComboBox = segmentedButton(AcksType.values().toList()) { StringUtil.wordsToBeginFromUpperCase(it.name.lowercase()) }
          acksComboBox.selectedItem = AcksType.NONE
        }
      }.topGap(TopGap.NONE)
    }

    panel.border = BorderFactory.createEmptyBorder(0, 10, 0, 0)

    val scroll = JBScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      minimumSize = Dimension(panel.minimumSize.width, minimumSize.height)
      border = BorderFactory.createEmptyBorder()
    }

    val bottomWidthGroup = "ButtonAndComment"
    val bottomPanel = panel {
      row {
        cell(produceButton).widthGroup(bottomWidthGroup)
        progress.initCell(this, bottomWidthGroup)

      }
    }.apply {
      border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }

    JPanel(BorderLayout()).apply {
      add(scroll, BorderLayout.CENTER)
      add(bottomPanel, BorderLayout.SOUTH)
    }
  }

  private fun stopProduce() {
    producerClient.stop()
  }

  private fun startProduce(): Boolean {
    val topic = topicComboBox.item

    val validationInfo = topicComboBox.getValidationInfo()
                         ?: keyFieldComponent.getValidationInfo()
                         ?: valueFieldComponent.getValidationInfo()

    if (validationInfo != null) {
      progress.onValidationError()
      return true
    }


    val selectedTopicName = topic.name

    executeNotOnEdt {
      if (!keyFieldComponent.validateSchema())
        return@executeNotOnEdt
      if (!valueFieldComponent.validateSchema())
        return@executeNotOnEdt

      val key = keyFieldComponent.getProducerField()
      val value = valueFieldComponent.getProducerField()

      try {
        onStart()
        producerClient.start(kafkaManager,
                             selectedTopicName,
                             key,
                             value,
                             propertiesComponent.properties,
                             compressionComboBox.item,
                             acksComboBox.selectedItem ?: AcksType.NONE,
                             idempotenceCheckBox.isSelected,
                             forcePartitionField.value,
                             flowParams = flowController.getParams()
        ) { time, records ->
          invokeLater {
            progress.onUpdate()
            output.addBatchRows(time, records)
          }
        }
      }
      catch (t: Throwable) {
        RfsNotificationUtils.showExceptionMessage(project, t)
      }
      finally {
        invokeLater {
          onStop()
        }
      }
    }

    KafkaUsagesCollector.producedKeyValue.log(project, keyFieldComponent.fieldTypeComboBox.item,
                                              valueFieldComponent.fieldTypeComboBox.item)
    return false
  }

  private fun onStart() = invokeLater {
    produceButton.text = KafkaMessagesBundle.message("action.produce.stop")
    produceButton.icon = AllIcons.Actions.Suspend
    progress.onStart()
  }

  private fun onStop() = invokeLater {
    produceButton.text = KafkaMessagesBundle.message("kafka.producer.action.produce.title")
    produceButton.icon = AllIcons.Actions.Execute
    progress.onStop()
  }

  private val settingsPanel: JPanel by settingsPanelDelegate

  private val settingsSplitter = OnePixelSplitter().apply {
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE
    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
    proportion = 0.0001f
  }

  private val presetsSplitter = OnePixelSplitter().apply {
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE
    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
    proportion = 0.0001f

    secondComponent = settingsSplitter
  }

  internal val mainComponent = createCenterPanel()

  init {
    val settingsExpanded = PropertiesComponent.getInstance().getBoolean(SETTINGS_SHOW_ID, true)
    settingsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.settings"), { settingsPanel },
                                                     settingsExpanded,
                                                     listOf(SavePresetAction(
                                                       KafkaConfigStorage.instance.producerConfig) { getConfig() })).apply {
      addChangeListener {
        settingsSplitter.proportion = 0.0001f
        settingsSplitter.setResizeEnabled(this.expanded)
      }
    }
    settingsSplitter.setResizeEnabled(settingsExpanded)

    settingsSplitter.secondComponent = output.resultsSplitter

    val presetsExpanded = PropertiesComponent.getInstance().getBoolean(PRESETS_SHOW_ID, false)
    presetsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.presets"), {
      presets.component.apply {
        minimumSize = Dimension(max(minimumSize.width, 200), minimumSize.height)
      }
    }, presetsExpanded).apply {
      addChangeListener {
        presetsSplitter.proportion = 0.0001f
        presetsSplitter.setResizeEnabled(this.expanded)
      }
    }
    presetsSplitter.setResizeEnabled(presetsExpanded)

    restoreFromFile()

    topicComboBox.item = topic?.let { TopicInEditor(it) }
  }

  override fun dispose() {
    storeToFile()
  }


  private fun createCenterPanel(): JComponent = presetsSplitter


  private fun storeToFile() {
    if (isRestoring) {
      return
    }

    file.putUserData(STATE_KEY, ProducerEditorState(output.getElements(), getConfig()))
  }

  private fun restoreFromFile() {
    try {
      isRestoring = true

      val state = file.getUserData(STATE_KEY) ?: return
      output.replace(state.output)
      applyConfig(state.config)
    }
    finally {
      isRestoring = false
    }
  }

  private fun getConfig() = StorageProducerConfig(
    topic = topicComboBox.item?.name ?: "",

    keyType = keyFieldComponent.fieldTypeComboBox.item.name,
    key = keyFieldComponent.getValueText(),
    keySubject = valueFieldComponent.schemaComboBox.item?.schemaName ?: "",

    valueType = valueFieldComponent.fieldTypeComboBox.item.name,
    value = valueFieldComponent.getValueText(),
    valueSubject = valueFieldComponent.schemaComboBox.item?.schemaName ?: "",

    properties = propertiesComponent.properties,
    compression = compressionComboBox.item.name,
    acks = acksComboBox.selectedItem?.name ?: AcksType.NONE.name,
    idempotence = idempotenceCheckBox.isSelected,
    forcePartition = forcePartitionField.value,
    flowParams = flowController.getParams())


  private fun applyConfig(config: StorageProducerConfig) {
    topicComboBox.item = TopicInEditor(config.topic)
    keyFieldComponent.applyConfig(config)
    valueFieldComponent.applyConfig(config)


    acksComboBox.selectedItem = config.getAsks()
    propertiesComponent.properties = config.properties.toMutableList()
    compressionComboBox.item = config.getCompression()
    idempotenceCheckBox.isSelected = config.idempotence
    forcePartitionField.value = config.forcePartition

    flowController.setParams(config.flowParams ?: ProducerFlowParams())
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

  companion object {
    val STATE_KEY = Key<ProducerEditorState>("PRODUCER_STATE")

    private const val SETTINGS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.producer.settings.show"
    private const val PRESETS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.producer.presets.show"
  }
}