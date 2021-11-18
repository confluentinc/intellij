package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.json.JsonLanguage
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
import com.intellij.ui.*
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.IntegerField
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.editor.renders.FieldTypeRenderer
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.ProducerField
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.models.*
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.table.MaterialTable
import com.jetbrains.bigdatatools.table.TableResizeController
import com.jetbrains.bigdatatools.table.filters.TableFilterHeader
import com.jetbrains.bigdatatools.table.renderers.DateRenderer
import com.jetbrains.bigdatatools.table.renderers.DurationRenderer
import com.jetbrains.bigdatatools.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.ui.ExpansionPanel
import com.jetbrains.bigdatatools.ui.MigPanel
import net.miginfocom.layout.LC
import java.awt.BorderLayout
import java.awt.Dimension
import java.beans.PropertyChangeListener
import javax.swing.*
import kotlin.math.max

@Suppress("DuplicatedCode")
class KafkaProducerEditor(project: Project,
                          kafkaManager: KafkaDataManager,
                          private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private var isRestoring = false

  private val producerClient = kafkaManager.client.createProducerClient()
  val topics = kafkaManager.getTopics()

  private val propertiesComponent = PropertiesTable("")

  private val topicComboBox = KafkaEditorUtils.createTopicComboBox(this, kafkaManager)

  private val acksComboBox = ComboBox(AcksType.values()).apply {
    renderer = CustomListCellRenderer<AcksType> { it.name.lowercase() }
    item = AcksType.NONE
  }

  private val idempotenceCheckBox = CheckBox(KafkaMessagesBundle.message("producer.idempotence.label")).apply {
    addChangeListener {
      acksComboBox.isEnabled = !isSelected
    }
  }

  private val compressionComboBox = ComboBox(RecordCompression.values()).apply {
    renderer = CustomListCellRenderer<RecordCompression> { it.name.lowercase() }
    selectedIndex = 0
  }

  private val keyComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
      mainComponent.revalidate()
    }
  }

  private val valueComboBox = ComboBox(FieldType.values()).apply {
    renderer = FieldTypeRenderer()
    selectedItem = FieldType.STRING
    addItemListener {
      updateVisibility()
      mainComponent.revalidate()
    }
  }

  private val keyJson = createJsonTextArea(project)
  private val valueJson = createJsonTextArea(project)

  private val keyIntegerField = IntegerField().apply { emptyText.text = "Optional" }
  private val valueIntegerField = IntegerField().apply { emptyText.text = "Optional" }

  private val keyDoubleField = doubleField().apply { emptyText.text = "Optional" }
  private val valueDoubleField = doubleField().apply { emptyText.text = "Optional" }

  private val keyStringField = JBTextField().apply { emptyText.text = "Optional" }
  private val valueStringField = JBTextField().apply { emptyText.text = "Optional" }

  private val forcePartitionField = IntegerField().apply {
    isCanBeEmpty = true
    defaultValue = -1
  }

  //ToDo "offset" temporary removed because always -1
  private val outputModel = ListTableModel(ArrayList<ProducerResultMessage>(),
    listOf("key", "value", "timestamp", "partition", "duration")) { data, index ->
    when (index) {
      0 -> data.key
      1 -> data.value
      2 -> data.timestamp
      //  3 -> data.offset
      4 -> data.partition
      5 -> data.duration
      else -> ""
    }
  }

  private val outputTableDelegate = lazy {
    MaterialTable(outputModel, outputModel.columnModel).apply {
      TableResizeController.installOn(this).apply {
        setResizePriorityList("value")
        mode = TableResizeController.Mode.PRIOR_COLUMNS_LIST
      }

      tableHeader.border = JBUI.Borders.empty()
      outputModel.columnModel.columns.asIterator().forEach {
        if (it.headerValue == "timestamp") {
          it.cellRenderer = DateRenderer()
        }
        else if (it.headerValue == "duration") {
          it.cellRenderer = DurationRenderer()
        }
      }
      TableFilterHeader(this)
    }
  }
  private val outputTable: MaterialTable by outputTableDelegate

  private val produceButton = JButton(KafkaMessagesBundle.message("kafka.producer.action.produce.title"), AllIcons.Actions.Execute).also {
    it.addActionListener {
      val topic = topicComboBox.item
      if (topic == null || topic.name.isBlank()) {
        Messages.showErrorDialog(kafkaManager.project,
          KafkaMessagesBundle.message("producer.error.topic.empty"),
          KafkaMessagesBundle.message("producer.error.topic.empty.title"))
        return@addActionListener
      }
      val selectedTopicName = topic.name

      val key = ProducerField(keyComboBox.item!!, getKey())
      val value = ProducerField(valueComboBox.item!!, getValue())

      getConfig()

      val result = producerClient.sentMessage(selectedTopicName, key, value, propertiesComponent.properties, compressionComboBox.item,
        acksComboBox.item, idempotenceCheckBox.isSelected, forcePartitionField.value)
      outputModel.addElement(result)
    }
  }

  private val savePresetButton = JButton(KafkaMessagesBundle.message("action.save.preset")).apply {
    addActionListener {
      KafkaConfigStorage.instance.producerConfig.addConfig(getConfig())
    }
  }

  private fun getConfig() = RunProducerConfig(topicComboBox.item?.name ?: "", keyType = keyComboBox.item, key = getKey(),
    valueType = valueComboBox.item, value = getValue(),
    properties = propertiesComponent.properties, compression = compressionComboBox.item,
    acks = acksComboBox.item, idempotence = idempotenceCheckBox.isSelected,
    forcePartition = forcePartitionField.value)

  private val clearButton = JButton(KafkaMessagesBundle.message("action.clear.output")).apply {
    addActionListener {
      outputModel.clear()
    }
  }

  private val presetsDelegate = lazy {
    val presets = ProducerPresets()
    Disposer.register(this, presets)
    presets.onApply = { applyConfig(it) }
    presets
  }
  private val presets: ProducerPresets by presetsDelegate

  private val settingsPanelDelegate = lazy {
    val panel = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {
      gapLeft = true
      row(KafkaMessagesBundle.message("producer.topics"), topicComboBox)
      row(KafkaMessagesBundle.message("producer.key"), keyComboBox)
      add(keyJson, UiUtil.growXSpanXWrap)
      add(keyIntegerField, UiUtil.growXSpanXWrap)
      add(keyDoubleField, UiUtil.growXSpanXWrap)
      add(keyStringField, UiUtil.growXSpanXWrap)

      row(KafkaMessagesBundle.message("producer.value"), valueComboBox)
      add(valueJson, UiUtil.growXSpanXWrap)
      add(valueIntegerField, UiUtil.growXSpanXWrap)
      add(valueDoubleField, UiUtil.growXSpanXWrap)
      add(valueStringField, UiUtil.growXSpanXWrap)

      title(KafkaMessagesBundle.message("producer.title.options"))
      row(KafkaMessagesBundle.message("producer.forcePartition"), forcePartitionField)
      row(KafkaMessagesBundle.message("record.headers.label"))
      block(propertiesComponent.getComponent())

      row(KafkaMessagesBundle.message("producer.compression"), compressionComboBox)
      row(KafkaMessagesBundle.message("producer.asks"), acksComboBox)
      add(idempotenceCheckBox, UiUtil.gapLeftSpanXWrap)

      gapLeft = false
    }

    val scroll = JBScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      minimumSize = Dimension(panel.minimumSize.width, minimumSize.height)
      border = BorderFactory.createEmptyBorder()
    }

    val bottomPanel = JPanel().apply {
      add(clearButton)
      add(produceButton)
      add(savePresetButton)
    }

    JPanel(BorderLayout()).apply {
      add(scroll, BorderLayout.CENTER)
      add(bottomPanel, BorderLayout.SOUTH)
    }
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

  private val mainComponent = createCenterPanel()

  init {
    settingsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.settings"), { settingsPanel },
      PropertiesComponent.getInstance().getBoolean(SETTINGS_SHOW_ID, true)).apply {
      addChangeListener {
        settingsSplitter.proportion = 0.0001f
        settingsSplitter.setResizeEnabled(this.expanded)
      }
    }

    val outputTableScroll = JBScrollPane(outputTable).apply { border = BorderFactory.createEmptyBorder() }
    settingsSplitter.secondComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.data"), { outputTableScroll },
      PropertiesComponent.getInstance().getBoolean(DATA_SHOW_ID, true)).apply {
      addChangeListener {
        settingsSplitter.proportion = 0.0001f
      }
    }

    presetsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.presets"), {
      presets.component.apply {
        minimumSize = Dimension(max(minimumSize.width, 200), minimumSize.height)
      }
    }, PropertiesComponent.getInstance().getBoolean(PRESETS_SHOW_ID, false)).apply {
      addChangeListener {
        presetsSplitter.proportion = 0.0001f
        presetsSplitter.setResizeEnabled(this.expanded)
      }
    }

    updateVisibility()
    restoreFromFile()
  }

  override fun dispose() {
    storeToFile()
  }

  private fun createCenterPanel(): JComponent = presetsSplitter

  private fun createJsonTextArea(project: Project) = EditorTextFieldProvider.getInstance()
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
      }, MonospaceEditorCustomization.getInstance())).apply {
      border = IdeBorderFactory.createBorder()
    }

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

    @Suppress("DuplicatedCode") when (keyComboBox.item!!) {
      FieldType.JSON -> keyJson.isVisible = true
      FieldType.STRING -> keyStringField.isVisible = true
      FieldType.LONG -> keyIntegerField.isVisible = true
      FieldType.DOUBLE -> keyDoubleField.isVisible = true
      FieldType.FLOAT -> keyDoubleField.isVisible = true
      FieldType.BASE64 -> keyStringField.isVisible = true
      FieldType.NULL -> Unit
    }

    @Suppress("DuplicatedCode") when (valueComboBox.item!!) {
      FieldType.JSON -> valueJson.isVisible = true
      FieldType.STRING -> valueStringField.isVisible = true
      FieldType.LONG -> valueIntegerField.isVisible = true
      FieldType.DOUBLE -> valueDoubleField.isVisible = true
      FieldType.FLOAT -> valueDoubleField.isVisible = true
      FieldType.BASE64 -> valueStringField.isVisible = true
      FieldType.NULL -> Unit
    }
  }

  private fun getKey() = when (keyComboBox.item!!) {
    FieldType.JSON -> keyJson.text
    FieldType.STRING -> keyStringField.text
    FieldType.LONG -> keyIntegerField.text
    FieldType.DOUBLE -> keyDoubleField.text
    FieldType.FLOAT -> keyDoubleField.text
    FieldType.BASE64 -> keyStringField.text
    FieldType.NULL -> ""
  }

  private fun getValue() = when (valueComboBox.item!!) {
    FieldType.JSON -> valueJson.text
    FieldType.STRING -> valueStringField.text
    FieldType.LONG -> valueIntegerField.text
    FieldType.DOUBLE -> valueDoubleField.text
    FieldType.FLOAT -> valueDoubleField.text
    FieldType.BASE64 -> valueStringField.text
    FieldType.NULL -> ""
  }

  private fun storeToFile() {
    if (isRestoring) {
      return
    }
    file.putUserData(STATE_KEY, ProducerEditorState(outputModel.elements().toList(), getConfig()))
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

  private fun applyConfig(config: RunProducerConfig) {
    topicComboBox.item = TopicInEditor(config.topic)
    keyComboBox.item = config.keyType

    when (config.keyType) {
      FieldType.JSON -> keyJson.text = config.key
      FieldType.STRING -> keyStringField.text = config.key
      FieldType.LONG -> keyIntegerField.text = config.key
      FieldType.DOUBLE -> keyDoubleField.text = config.key
      FieldType.FLOAT -> keyDoubleField.text = config.key
      FieldType.BASE64 -> keyStringField.text = config.key
      FieldType.NULL -> {
      }
    }
    valueComboBox.item = config.valueType

    when (config.valueType) {
      FieldType.JSON -> valueJson.text = config.value
      FieldType.STRING -> valueStringField.text = config.value
      FieldType.LONG -> valueIntegerField.text = config.value
      FieldType.DOUBLE -> valueDoubleField.text = config.value
      FieldType.FLOAT -> valueDoubleField.text = config.value
      FieldType.BASE64 -> valueStringField.text = config.value
      FieldType.NULL -> {
      }
    }

    acksComboBox.item = config.acks
    propertiesComponent.properties = config.properties.toMutableList()
    compressionComboBox.item = config.compression
    idempotenceCheckBox.isSelected = config.idempotence
    forcePartitionField.value = config.forcePartition
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

    private const val DATA_SHOW_ID = "com.jetbrains.bigdatatools.kafka.producer.data.show"
    private const val SETTINGS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.producer.settings.show"
    private const val PRESETS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.producer.presets.show"
  }
}