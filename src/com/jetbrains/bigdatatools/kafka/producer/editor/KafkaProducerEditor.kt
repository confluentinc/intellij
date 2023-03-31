package com.jetbrains.bigdatatools.kafka.producer.editor

import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
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
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
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
import com.jetbrains.bigdatatools.common.ui.SimpleDumbAwareAction
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.common.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.common.editor.SavePresetAction
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.ProducerField
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerEditorState
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerResultMessage
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.registry.ConfluentRegistryStrategy
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import software.amazon.awssdk.services.glue.model.DataFormat
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
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
    }.also {
      it.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          keySchemaValidationError = null
        }
      }, this)
    }
  }

  private val valueJsonField: EditorTextField by lazy {
    KafkaEditorUtils.createJsonTextArea(project).withValidator(this, ::validateValue).apply {
      setDisposedWith(this@KafkaProducerEditor)
    }.also {
      it.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          valueSchemaValidationError = null
        }
      }, this)
    }
  }

  private var keySchemaValidationError: Throwable? = null
  private var valueSchemaValidationError: Throwable? = null

  private val keyField = JBTextField().apply { emptyText.text = "Optional" }.withValidator(this, ::validateKey)
  private val valueField = JBTextField().apply { emptyText.text = "Optional" }.withValidator(this, ::validateValue)

  private val keyComboBox = createFieldTypeComboBox(keyJsonField, keyField)
  private val valueComboBox = createFieldTypeComboBox(valueJsonField, valueField)

  private val keyStrategyComboBox = createStrategyComboBox().apply {
    addActionListener {
      updateVisibilityOfRegistrySchemaSelector()
    }
  }

  private val valueStrategyComboBox = createStrategyComboBox().apply {
    addActionListener {
      updateVisibilityOfRegistrySchemaSelector()
    }
  }

  private val keySchemaComboBox = KafkaEditorUtils.createSchemaComboBox(this, kafkaManager).apply { isVisible = false }
  private val valueSchemaComboBox = KafkaEditorUtils.createSchemaComboBox(this, kafkaManager).apply { isVisible = false }

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
                                                  keySubject = keySchemaComboBox.item?.schemaName ?: "",
                                                  keyRegistry = keySchemaComboBox.item?.registryName ?: "",
                                                  valueSubject = valueSchemaComboBox.item?.schemaName ?: "",
                                                  valueRegistry = valueSchemaComboBox.item?.registryName ?: "")

  private val presetsDelegate = lazy {
    val presets = ProducerPresets()
    Disposer.register(this, presets)
    presets.onApply = { applyConfig(it) }
    presets
  }

  private val presets: ProducerPresets by presetsDelegate

  private val settingsPanelDelegate = lazy {
    val panel = panel {
      row(KafkaMessagesBundle.message("producer.topics")) { cell(topicComboBox).align(AlignX.FILL).resizableColumn() }

      row(KafkaMessagesBundle.message("producer.key")) { cell(keyComboBox) }
      indent {
        row {
          cell(keyStrategyComboBox).onChanged { keySchemaValidationError = null }
        }
        row {
          cell(keySchemaComboBox).onChanged { keySchemaValidationError = null }.align(AlignX.FILL).resizableColumn()
        }
      }
      row { cell(keyJsonField).align(AlignX.FILL).resizableColumn() }
      row {
        cell(keyField).align(AlignX.FILL).resizableColumn().onChanged {
          keySchemaValidationError = null
        }
      }

      row(KafkaMessagesBundle.message("producer.value")) { cell(valueComboBox) }
      indent {
        row {
          cell(valueStrategyComboBox).onChanged { valueSchemaValidationError = null }
        }
        row {
          cell(valueSchemaComboBox).onChanged { valueSchemaValidationError = null }.align(AlignX.FILL).resizableColumn()
        }
      }
      row { cell(valueJsonField).align(AlignX.FILL).resizableColumn() }
      row {
        cell(valueField).align(AlignX.FILL).resizableColumn().onChanged {
          valueSchemaValidationError = null
        }
      }

      collapsibleGroup(KafkaMessagesBundle.message("producer.title.options")) {
        row(KafkaMessagesBundle.message("producer.forcePartition")) { cell(forcePartitionField).align(AlignX.FILL).resizableColumn() }
        row(KafkaMessagesBundle.message("record.headers.label")) {}
        row { cell(propertiesComponent.getComponent()).align(AlignX.FILL).resizableColumn() }

        row(KafkaMessagesBundle.message("producer.compression")) { cell(compressionComboBox).align(AlignX.FILL).resizableColumn() }
        row { cell(idempotenceCheckBox).align(AlignX.FILL).resizableColumn() }
        row(KafkaMessagesBundle.message("producer.asks")) { cell(acksComboBox).align(AlignX.FILL).resizableColumn() }
      }
    }

    panel.border = BorderFactory.createEmptyBorder(0, 10, 0, 0)

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
          keySchemaValidationError = null
          val key = try {
            ProducerField(keyComboBox.item, getKey(), keyStrategyComboBox.item, getSchemaFor(isKey = true),
                          registryName = keySchemaComboBox.item.registryName,
                          schemaName = keySchemaComboBox.item.schemaName.let { if (it == RegistrySchemaInEditor.GLUE_DEFAULT.schemaName) topicComboBox.item.name else it })
          }
          catch (t: Throwable) {
            keySchemaValidationError = t
            null
          }

          valueSchemaValidationError = null
          val value = try {
            ProducerField(valueComboBox.item, getValue(), valueStrategyComboBox.item, getSchemaFor(isKey = false),
                          schemaName = valueSchemaComboBox.item.schemaName.let { if (it == RegistrySchemaInEditor.GLUE_DEFAULT.schemaName) topicComboBox.item.name else it },
                          registryName = valueSchemaComboBox.item.registryName)
          }
          catch (t: Throwable) {
            valueSchemaValidationError = t
            null
          }

          if (key != null && value != null) {
            try {
              val result = producerClient.sentMessage(kafkaManager,
                                                      selectedTopicName,
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
              RfsNotificationUtils.showExceptionMessage(project, t)
            }

          }

          keyField.revalidateComponent()
          valueField.revalidateComponent()
          keyJsonField.revalidateComponent()
          valueJsonField.revalidateComponent()
        }

        KafkaUsagesCollector.producedKeyValue.log(project, keyComboBox.item, valueComboBox.item)
      }
    }

    val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
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
    if (fieldType !in FieldType.registryValues)
      return null

    return when (kafkaManager.registryType) {
      KafkaRegistryType.NONE -> null
      KafkaRegistryType.CONFLUENT -> parseConfluentSchema(isKey)
      KafkaRegistryType.AWS_GLUE -> parseGlueSchema(isKey)
    }
  }

  private fun parseGlueSchema(isKey: Boolean): ParsedSchema {
    val schemaName = (if (isKey) keySchemaComboBox.item.schemaName else valueSchemaComboBox.item.schemaName).let {
      if (it == RegistrySchemaInEditor.GLUE_DEFAULT.schemaName) topicComboBox.item.name else it
    }
    val registryName = if (isKey) keySchemaComboBox.item.registryName else valueSchemaComboBox.item.registryName

    val detailedInfo = kafkaManager.glueSchemaRegistry?.loadDetailedSchemaInfo(registryName, schemaName) ?: throw Exception(
      KafkaMessagesBundle.message("error.glue.schema.is.not.found", schemaName, registryName))

    val type = (if (isKey) keyComboBox else valueComboBox).item
    val dataFormat = when (type) {
      FieldType.AVRO_REGISTRY -> DataFormat.AVRO
      FieldType.PROTOBUF_REGISTRY -> DataFormat.PROTOBUF
      FieldType.JSON_REGISTRY -> DataFormat.JSON
      else -> null
    }

    val expectedFormat = detailedInfo.schemaResponse.dataFormat()
    if (dataFormat != expectedFormat) {
      throw Exception(KafkaMessagesBundle.message("error.glue.wrong.format", dataFormat?.name ?: "<unknown>", expectedFormat))
    }
    return KafkaRegistryUtil.parseSchema(schemaType = detailedInfo.schemaResponse.dataFormatAsString(),
                                         detailedInfo.versionResponse.schemaDefinition(), emptyList()).getOrThrow()
  }


  private fun parseConfluentSchema(isKey: Boolean): ParsedSchema {
    val strategy = if (isKey) keyStrategyComboBox.item else valueStrategyComboBox.item
    val schemaName = when (strategy) {
      ConfluentRegistryStrategy.TOPIC_NAME -> {
        val suffix = if (isKey) "key" else "value"
        "${topicComboBox.item.name}-$suffix"
      }
      ConfluentRegistryStrategy.RECORD_NAME, ConfluentRegistryStrategy.TOPIC_RECORD_NAME, ConfluentRegistryStrategy.CUSTOM ->
        if (isKey) keySchemaComboBox.item.schemaName else valueSchemaComboBox.item.schemaName
      else -> error("Wrong Registry strategy")
    }

    val schemaMetadata = kafkaManager.confluentSchemaRegistry?.getRegistrySchema(schemaName)?.meta
                         ?: error("Schema `$schemaName` is not found")
    return KafkaRegistryUtil.parseSchema(schemaMetadata.schemaType, schemaMetadata.schema, schemaMetadata.references).getOrThrow()
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

    keySchemaValidationError = null
    valueSchemaValidationError = null

    when (keyComboBox.item!!) {
      FieldType.JSON -> keyJsonField.isVisible = true
      FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> keyField.isVisible = true
      FieldType.NULL -> Unit
      FieldType.AVRO_REGISTRY, FieldType.PROTOBUF_REGISTRY, FieldType.JSON_REGISTRY -> {
        keyJsonField.isVisible = true
        keyStrategyComboBox.isVisible = kafkaManager.isConfluentSchemaRegistryEnabled
      }
    }

    when (valueComboBox.item!!) {
      FieldType.JSON -> valueJsonField.isVisible = true
      FieldType.STRING, FieldType.LONG, FieldType.DOUBLE, FieldType.FLOAT, FieldType.BASE64 -> valueField.isVisible = true
      FieldType.AVRO_REGISTRY, FieldType.PROTOBUF_REGISTRY, FieldType.JSON_REGISTRY -> {
        valueStrategyComboBox.isVisible = kafkaManager.isConfluentSchemaRegistryEnabled
        valueJsonField.isVisible = true
      }
      FieldType.NULL -> Unit
    }

    updateVisibilityOfRegistrySchemaSelector()
  }

  private fun updateVisibilityOfRegistrySchemaSelector() {
    keySchemaComboBox.isVisible = keyComboBox.item in FieldType.registryValues &&
                                  (kafkaManager.isGlueSchemaRegistryEnabled || keyStrategyComboBox.item != ConfluentRegistryStrategy.TOPIC_NAME)

    valueSchemaComboBox.isVisible = valueComboBox.item in FieldType.registryValues &&
                                    (kafkaManager.isGlueSchemaRegistryEnabled || valueStrategyComboBox.item != ConfluentRegistryStrategy.TOPIC_NAME)
  }

  private fun createFieldTypeComboBox(jsonField: EditorTextField, field: JTextComponent) =
    ComboBox(FieldType.values()).apply {
      renderer = CustomListCellRenderer<FieldType> { it.title }
      selectedItem = FieldType.STRING
      addActionListener {
        updateVisibility()
        jsonField.revalidateComponent()
        field.revalidateComponent()
        mainComponent.revalidate()
      }
    }

  private fun createStrategyComboBox() = ComboBox(ConfluentRegistryStrategy.producerOptions).apply {
    renderer = CustomListCellRenderer<ConfluentRegistryStrategy> { it.presentable }
    selectedItem = ConfluentRegistryStrategy.TOPIC_NAME
    addActionListener {
      updateVisibility()
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun validateKey(text: String): String? {
    if (keySchemaValidationError != null) return keySchemaValidationError?.toPresentableText()
    return validate(keyComboBox.item, getKey())
  }

  @Suppress("UNUSED_PARAMETER")
  private fun validateValue(text: String): String? {
    if (valueSchemaValidationError != null) return valueSchemaValidationError?.toPresentableText()
    return validate(valueComboBox.item, getValue())
  }

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
        keySchemaComboBox.item = RegistrySchemaInEditor(config.keySubject, config.keyRegistry)
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
        valueSchemaComboBox.item = RegistrySchemaInEditor(config.valueSubject, config.valueRegistry)
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