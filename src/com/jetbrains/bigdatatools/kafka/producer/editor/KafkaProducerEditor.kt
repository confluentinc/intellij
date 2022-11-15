package com.jetbrains.bigdatatools.kafka.producer.editor

import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAwareAction
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
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.common.settings.getValidationInfo
import com.jetbrains.bigdatatools.common.settings.revalidateComponent
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.table.MaterialTable
import com.jetbrains.bigdatatools.common.table.MaterialTableUtils
import com.jetbrains.bigdatatools.common.table.TableResizeController
import com.jetbrains.bigdatatools.common.table.extension.TableFirstRowAdded
import com.jetbrains.bigdatatools.common.table.filters.TableFilterHeader
import com.jetbrains.bigdatatools.common.table.renderers.DateRenderer
import com.jetbrains.bigdatatools.common.table.renderers.DurationRenderer
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.ExpansionPanel
import com.jetbrains.bigdatatools.common.ui.MigPanel
import com.jetbrains.bigdatatools.common.ui.SimpleDumbAwareAction
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.editor.SavePresetAction
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.ProducerField
import com.jetbrains.bigdatatools.kafka.common.models.SubjectInEditor
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerEditorState
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerResultMessage
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryStrategy
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import java.awt.BorderLayout
import java.awt.Dimension
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.math.max

class KafkaProducerEditor(project: Project,
                          private val kafkaManager: KafkaDataManager,
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

  private val keyJsonField: EditorTextField by lazy {
    KafkaEditorUtils.createJsonTextArea(project).withValidator(this, ::validateKey).apply {
      setDisposedWith(this@KafkaProducerEditor)
    }
  }

  private val valueJsonField: EditorTextField by lazy {
    KafkaEditorUtils.createJsonTextArea(project).withValidator(this, ::validateValue).apply {
      setDisposedWith(this@KafkaProducerEditor)
    }
  }

  private val keyField = JBTextField().apply { emptyText.text = "Optional" }.withValidator(this, ::validateKey)
  private val valueField = JBTextField().apply { emptyText.text = "Optional" }.withValidator(this, ::validateValue)

  private val keyComboBox = createFieldTypeComboBox(keyJsonField, keyField)
  private val valueComboBox = createFieldTypeComboBox(valueJsonField, valueField)

  private val keyStrategyComboBox = createStrategyComboBox().also {
    it.addItemListener {
      updateVisibilityOfRegistrySubject()
    }
  }

  private val valueStrategyComboBox = createStrategyComboBox().also {
    it.addItemListener {
      updateVisibilityOfRegistrySubject()
    }
  }

  private val keySubjectComboBox = KafkaEditorUtils.createSubjectComboBox(this, kafkaManager)
  private val valueSubjectComboBox = KafkaEditorUtils.createSubjectComboBox(this, kafkaManager)

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
      3 -> data.partition
      4 -> data.duration
      else -> ""
    }
  }.apply {
    columnClasses = listOf(Object::class.java, Object::class.java, Date::class.java, Int::class.java, Int::class.java)
  }

  private val outputTableDelegate = lazy {
    MaterialTable(outputModel, outputModel.columnModel).apply {
      tableHeader.border = BorderFactory.createEmptyBorder()
      outputModel.columnModel.columns.asIterator().forEach {
        if (it.headerValue == "timestamp") {
          it.cellRenderer = DateRenderer()
        }
        else if (it.headerValue == "duration") {
          it.cellRenderer = DurationRenderer()
        }
      }
      TableFilterHeader(this)
      val resizeController = TableResizeController(this).apply {
        setResizePriorityList("value")
        mode = TableResizeController.Mode.PRIOR_COLUMNS_LIST
      }

      MaterialTableUtils.fitColumnsWidth(this)
      resizeController.componentResized()

      TableFirstRowAdded(this) {
        MaterialTableUtils.fitColumnsWidth(this)
        resizeController.componentResized()
      }
      setupTablePopupMenu(this)
    }
  }
  private val outputTable: MaterialTable by outputTableDelegate

  private fun getConfig() = StorageProducerConfig(topicComboBox.item?.name ?: "",
                                                  keyType = keyComboBox.item, key = getKey(),
                                                  valueType = valueComboBox.item, value = getValue(),
                                                  properties = propertiesComponent.properties, compression = compressionComboBox.item,
                                                  acks = acksComboBox.item, idempotence = idempotenceCheckBox.isSelected,
                                                  forcePartition = forcePartitionField.value,
                                                  keyStrategy = keyStrategyComboBox.item,
                                                  valueStrategy = valueStrategyComboBox.item,
                                                  keySubject = keySubjectComboBox.item.name,
                                                  valueSubject = valueSubjectComboBox.item.name)

  private val presetsDelegate = lazy {
    val presets = ProducerPresets()
    Disposer.register(this, presets)
    presets.onApply = { applyConfig(it) }
    presets
  }

  private val presets: ProducerPresets by presetsDelegate

  private val settingsPanelDelegate = lazy {
    val panel = MigPanel(UiUtil.insets10FillXHidemode3).apply {
      row(KafkaMessagesBundle.message("producer.topics"), topicComboBox)
      row(KafkaMessagesBundle.message("producer.key"), keyComboBox)
      add(keyStrategyComboBox, UiUtil.growXSpanXWrap)
      add(keySubjectComboBox, UiUtil.growXSpanXWrap)
      add(keyJsonField, UiUtil.growXSpanXWrap)
      add(keyField, UiUtil.growXSpanXWrap)

      row(KafkaMessagesBundle.message("producer.value"), valueComboBox)
      add(valueStrategyComboBox, UiUtil.growXSpanXWrap)
      add(valueSubjectComboBox, UiUtil.growXSpanXWrap)
      add(valueJsonField, UiUtil.growXSpanXWrap)
      add(valueField, UiUtil.growXSpanXWrap)

      title(KafkaMessagesBundle.message("producer.title.options"))
      row(KafkaMessagesBundle.message("producer.forcePartition"), forcePartitionField)
      row(KafkaMessagesBundle.message("record.headers.label"))
      block(propertiesComponent.getComponent())

      row(KafkaMessagesBundle.message("producer.compression"), compressionComboBox)
      add(idempotenceCheckBox, UiUtil.spanXWrap)
      row(KafkaMessagesBundle.message("producer.asks"), acksComboBox)
    }

    val scroll = JBScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      minimumSize = Dimension(panel.minimumSize.width, minimumSize.height)
      border = BorderFactory.createEmptyBorder()
    }

    val produceButton = JButton(KafkaMessagesBundle.message("kafka.producer.action.produce.title"), AllIcons.Actions.Execute).also {
      it.addActionListener {
        val topic = topicComboBox.item
        if (topic == null || topic.name.isBlank()) {
          Messages.showErrorDialog(kafkaManager.project,
                                   KafkaMessagesBundle.message("producer.error.topic.empty"),
                                   KafkaMessagesBundle.message("producer.error.topic.empty.title"))
          return@addActionListener
        }

        if (keyField.getValidationInfo() != null ||
            keyJsonField.getValidationInfo() != null ||
            valueField.getValidationInfo() != null ||
            valueJsonField.getValidationInfo() != null) {
          return@addActionListener
        }

        val selectedTopicName = topic.name


        executeNotOnEdt {
          try {
            val key = ProducerField(keyComboBox.item!!, getKey(), keyStrategyComboBox.item, getSchemaFor(isKey = true))
            val value = ProducerField(valueComboBox.item!!, getValue(), valueStrategyComboBox.item, getSchemaFor(isKey = false))

            val result = producerClient.sentMessage(selectedTopicName,
                                                    key,
                                                    value,
                                                    propertiesComponent.properties,
                                                    compressionComboBox.item,
                                                    acksComboBox.item,
                                                    idempotenceCheckBox.isSelected,
                                                    forcePartitionField.value)
            invokeLater {
              outputModel.addElement(result)
            }
          }
          catch (t: Throwable) {
            invokeLater {
              RfsNotificationUtils.showExceptionMessage(project, t, title = KafkaMessagesBundle.message("kafka.producer.error"))
            }
          }
        }

        KafkaUsagesCollector.producedKeyValue.log(project, keyComboBox.item, valueComboBox.item)
      }
    }

    val bottomPanel = MigPanel(UiUtil.insets10FillXHidemode3).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
      add(produceButton)
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

    val clearButton = object : DumbAwareAction(KafkaMessagesBundle.message("action.clear.output"), null, AllIcons.Actions.GC) {
      override fun actionPerformed(e: AnActionEvent) {
        outputModel.clear()
      }
    }

    val outputTableScroll = JBScrollPane(outputTable).apply { border = BorderFactory.createEmptyBorder() }
    settingsSplitter.secondComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.data"), { outputTableScroll },
                                                      PropertiesComponent.getInstance().getBoolean(DATA_SHOW_ID, true),
                                                      listOf(clearButton)
    ).apply {
      addChangeListener {
        settingsSplitter.proportion = 0.0001f
      }
    }

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

    updateVisibility()
    restoreFromFile()
  }

  override fun dispose() {
    storeToFile()
  }

  private fun getSchemaFor(isKey: Boolean): ParsedSchema? {
    val fieldType = if (isKey) keyComboBox.item else valueComboBox.item
    val suffix = if (isKey) "key" else "value"
    val strategy = if (isKey) keyStrategyComboBox.item else valueStrategyComboBox.item
    val subject = if (isKey) keySubjectComboBox.item.name else valueSubjectComboBox.item.name
    if (fieldType !in FieldType.registryValues)
      return null

    val subjectName: String = when (strategy) {
      KafkaRegistryStrategy.TOPIC_NAME -> "${topicComboBox.item.name}-$suffix"
      KafkaRegistryStrategy.CUSTOM, KafkaRegistryStrategy.RECORD_NAME, KafkaRegistryStrategy.TOPIC_RECORD_NAME -> subject
      null -> return null
    }

    val schemaMetadata = kafkaManager.getRegistrySchema(subjectName)?.meta
                         ?: error("Schema `$subjectName` is not found")
    return KafkaRegistryUtil.parseSchema(schemaMetadata)
  }

  private fun setupTablePopupMenu(table: JTable) {
    val clearAction = SimpleDumbAwareAction(KafkaMessagesBundle.message("action.clear.output")) { outputModel.clear() }
    PopupHandler.installPopupMenu(table, DefaultActionGroup().apply {
      (ActionManager.getInstance().getAction("BdIde.TableEditor.PopupActionGroup") as? ActionGroup)?.let { addAll(it) }
      addSeparator()
      addAction(clearAction)
    }, "KafkaProducerEditor")
  }

  private fun createCenterPanel(): JComponent = presetsSplitter

  private fun updateVisibility() {
    keyStrategyComboBox.isVisible = false
    valueStrategyComboBox.isVisible = false

    keyJsonField.isVisible = false
    valueJsonField.isVisible = false

    keyField.isVisible = false
    valueField.isVisible = false

    when (keyComboBox.item!!) {
      FieldType.JSON -> keyJsonField.isVisible = true
      FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> keyField.isVisible = true
      FieldType.NULL -> Unit
      FieldType.AVRO_REGISTRY, FieldType.PROTOBUF_REGISTRY, FieldType.JSON_REGISTRY -> {
        keyJsonField.isVisible = true
        keyStrategyComboBox.isVisible = true
      }
    }

    when (valueComboBox.item!!) {
      FieldType.JSON -> valueJsonField.isVisible = true
      FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> valueField.isVisible = true
      FieldType.AVRO_REGISTRY, FieldType.PROTOBUF_REGISTRY, FieldType.JSON_REGISTRY -> {
        valueStrategyComboBox.isVisible = true
        valueJsonField.isVisible = true
      }
      FieldType.NULL -> Unit
    }

    updateVisibilityOfRegistrySubject()
  }

  private fun updateVisibilityOfRegistrySubject() {
    keySubjectComboBox.isVisible = keyComboBox.item in FieldType.registryValues &&
                                   keyStrategyComboBox.item != KafkaRegistryStrategy.TOPIC_NAME

    valueSubjectComboBox.isVisible = valueComboBox.item in FieldType.registryValues &&
                                     valueStrategyComboBox.item != KafkaRegistryStrategy.TOPIC_NAME
  }

  private fun createFieldTypeComboBox(jsonField: EditorTextField, field: JTextComponent) =
    ComboBox(FieldType.values()).apply {
      renderer = CustomListCellRenderer<FieldType> { it.title }
      selectedItem = FieldType.STRING
      addItemListener {
        updateVisibility()
        jsonField.revalidateComponent()
        field.revalidateComponent()
        mainComponent.revalidate()
      }
    }

  private fun createStrategyComboBox() = ComboBox(KafkaRegistryStrategy.producerOptions).apply {
    renderer = CustomListCellRenderer<KafkaRegistryStrategy> { it.presentable }
    selectedItem = KafkaRegistryStrategy.TOPIC_NAME
    addItemListener {
      updateVisibility()
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun validateKey(text: String): String? = validate(keyComboBox.item, getKey())

  @Suppress("UNUSED_PARAMETER")
  private fun validateValue(text: String): String? = validate(valueComboBox.item, getValue())

  private fun validate(type: FieldType, value: String): String? {
    return when (type) {
      FieldType.JSON -> {
        try {
          JsonParser.parseString(value)
          null
        }
        catch (iae: Exception) {
          iae.cause?.message ?: iae.message
        }
      }
      FieldType.STRING -> null
      FieldType.LONG -> if (value.toLongOrNull() != null) null else "'$value' is not a valid long value"
      FieldType.DOUBLE -> if (value.toDoubleOrNull() != null) null else "'$value' is not a valid double value"
      FieldType.FLOAT -> if (value.toFloatOrNull() != null) null else "'$value' is not a valid float value"
      FieldType.BASE64 -> {
        val decoder = Base64.getDecoder()
        try {
          decoder.decode(value)
          null
        }
        catch (iae: IllegalArgumentException) {
          iae.message
        }
      }
      FieldType.NULL -> null // Any value match null type
      FieldType.AVRO_REGISTRY, FieldType.PROTOBUF_REGISTRY, FieldType.JSON_REGISTRY -> try {
        JsonParser.parseString(value)
        null
      }
      catch (iae: Exception) {
        iae.cause?.message ?: iae.message
      }

    }
  }

  private fun getKey(): String = when (keyComboBox.item!!) {
    FieldType.JSON -> keyJsonField.text
    FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> keyField.text
    FieldType.NULL -> ""
    FieldType.AVRO_REGISTRY, FieldType.JSON_REGISTRY, FieldType.PROTOBUF_REGISTRY -> keyJsonField.text
  }

  private fun getValue(): String = when (valueComboBox.item!!) {
    FieldType.JSON -> valueJsonField.text
    FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> valueField.text
    FieldType.NULL -> ""
    FieldType.AVRO_REGISTRY, FieldType.JSON_REGISTRY, FieldType.PROTOBUF_REGISTRY -> valueJsonField.text
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

  private fun applyConfig(config: StorageProducerConfig) {
    topicComboBox.item = TopicInEditor(config.topic)
    keyComboBox.item = config.getKeyType()

    when (config.getKeyType()) {
      FieldType.JSON -> keyJsonField.text = config.key
      FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> keyField.text = config.key
      FieldType.NULL -> Unit
      FieldType.AVRO_REGISTRY, FieldType.JSON_REGISTRY, FieldType.PROTOBUF_REGISTRY -> {
        keyJsonField.text = config.key
        keyStrategyComboBox.item = config.keyStrategy
        keySubjectComboBox.item = SubjectInEditor(config.keySubject)
      }
    }
    valueComboBox.item = config.getValueType()

    when (config.getValueType()) {
      FieldType.JSON -> valueJsonField.text = config.value
      FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> valueField.text = config.value
      FieldType.NULL -> Unit
      FieldType.AVRO_REGISTRY, FieldType.JSON_REGISTRY, FieldType.PROTOBUF_REGISTRY -> {
        valueJsonField.text = config.value
        valueStrategyComboBox.item = config.valueStrategy
        valueSubjectComboBox.item = SubjectInEditor(config.valueSubject)
      }
    }

    acksComboBox.item = config.getAsks()
    propertiesComponent.properties = config.properties.toMutableList()
    compressionComboBox.item = config.getCompression()
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