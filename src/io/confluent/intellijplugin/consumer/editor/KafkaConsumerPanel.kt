package io.confluent.intellijplugin.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.enteredTextSatisfies
import com.intellij.ui.layout.not
import io.confluent.intellijplugin.common.editor.*
import io.confluent.intellijplugin.common.models.TopicInEditor
import io.confluent.intellijplugin.common.settings.KafkaConfigStorage
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.client.KafkaConsumerClient
import io.confluent.intellijplugin.consumer.models.*
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.getValidationInfo
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.core.ui.ExpansionPanel
import io.confluent.intellijplugin.core.ui.MultiSplitter
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.core.util.withPluginClassLoader
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.util.KafkaConsumerGroupChangeOffsetProcess
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.math.max

class KafkaConsumerPanel(
    val project: Project,
    internal val kafkaManager: KafkaDataManager,
    private val file: VirtualFile
) : Disposable {
    private val consumerClient: KafkaConsumerClient = KafkaConsumerClient(
        dataManager = kafkaManager,
        onStart = ::onStartConsume,
        onStop = ::onStopConsume
    )

    // Feature flag to toggle between Swing table and JCEF WebView
    // Enable via: -Dkafka.webview.enabled=true or set environment variable
    private val useWebView = System.getProperty("kafka.webview.enabled")?.toBoolean() ?: false

    private val output: IKafkaRecordsOutput = if (useWebView) {
        KafkaRecordsWebViewOutput(project, isProducer = false)
    } else {
        KafkaRecordsOutput(project, isProducer = false)
    }.also { Disposer.register(this, it) }

    private val startSpecificDate = TimestampTextField(this)
    private val limitSpecificDate = TimestampTextField(this)
    private val limitOffset = JBTextField(15)

    private val startOffset = JBTextField(15)
    private val startConsumerGroup = KafkaEditorUtils.createConsumerGroups(this, kafkaManager, withEmpty = false)

    private val startFromComboBox = ComboBox(ConsumerStartType.entries.toTypedArray()).apply {
        renderer = CustomListCellRenderer<ConsumerStartType> { it.title }
        item = ConsumerStartType.NOW
        addActionListener {
            updateStartWith()
            storeToUserData()
            getComponent().revalidate()
        }
    }

    private val limitComboBox = ComboBox(ConsumerLimitType.entries.toTypedArray()).apply {
        renderer = CustomListCellRenderer<ConsumerLimitType> { it.title }
        item = ConsumerLimitType.NONE
        addActionListener {
            updateLimit()
            storeToUserData()
            getComponent().revalidate()
        }
    }

    private val filterComboBox = ComboBox(ConsumerFilterType.entries.toTypedArray()).apply {
        renderer = CustomListCellRenderer<ConsumerFilterType> { it.title }
        item = ConsumerFilterType.NONE
        addActionListener {
            updateFilter()
            storeToUserData()
            getComponent().revalidate()
        }
    }

    private val progress = KafkaProducerConsumerProgressComponent()

    private val filterKeyField = JBTextField()
    private val filterValueField = JBTextField()
    private val filterHeadKeyField = JBTextField()
    private val filterHeadValueField = JBTextField()

    private val partitionField = JBTextField()
    private val consumerGroup = KafkaEditorUtils.createConsumerGroups(this, kafkaManager, withEmpty = true)

    val topicComboBox = KafkaEditorUtils.createTopicComboBox(this, kafkaManager).apply {
        prototypeDisplayValue = TopicInEditor("AverageName")
        addActionListener {
            storeToUserData()
            FileEditorManager.getInstance(project).updateFilePresentation(file)
        }
    }

    private val key = KafkaConsumerFieldComponent(project, this, isKey = true).also { Disposer.register(this, it) }
    private val value = KafkaConsumerFieldComponent(project, this, isKey = false).also { Disposer.register(this, it) }

    private val kafkaConsumerSettingsDelegate = lazy { KafkaConsumerSettings() }
    private val kafkaConsumerSettings: KafkaConsumerSettings by kafkaConsumerSettingsDelegate

    private val advancedSettings = ActionLink(KafkaMessagesBundle.message("settings.advanced")) {
        kafkaConsumerSettings.show()
    }

    private val consumeButton: JButton =
        JButton(KafkaMessagesBundle.message("action.consume.start.title"), AllIcons.Actions.Execute).apply {
            addActionListener {
                executeOnPooledThread {
                    try {
                        if (consumerClient.isRunning()) {
                            consumerClient.stop()
                            output.stop()
                        } else {
                            val validationInfo = topicComboBox.getValidationInfo() ?: key.getValidationInfo()
                            ?: value.getValidationInfo()
                            if (validationInfo != null) {
                                progress.onValidationError()
                                return@executeOnPooledThread
                            }

                            startConsume(kafkaManager.project)
                        }
                        updateVisibility()
                        storeToUserData()

                        invalidate()
                        repaint()
                    } catch (t: Throwable) {
                        @Suppress("DialogTitleCapitalization")
                        RfsNotificationUtils.notifyException(t, KafkaMessagesBundle.message("error.start.consumer"))
                    }
                }
            }
        }

    private val filterPanel = panel {
        row(KafkaMessagesBundle.message("label.filter.key")) {
            cell(filterKeyField).align(AlignX.FILL).resizableColumn()
        }
        row(KafkaMessagesBundle.message("label.filter.value")) {
            cell(filterValueField).align(AlignX.FILL).resizableColumn()
        }
        row(KafkaMessagesBundle.message("label.filter.head.key")) {
            cell(filterHeadKeyField).align(AlignX.FILL).resizableColumn()
        }
        row(KafkaMessagesBundle.message("label.filter.head.value")) {
            cell(filterHeadValueField).align(AlignX.FILL).resizableColumn()
        }
    }

    private val startSpecificDateBlock = AtomicBooleanProperty(false)
    private val startOffsetBlock = AtomicBooleanProperty(false)
    private val startConsumerGroupBlock = AtomicBooleanProperty(false)

    private val limitSpecificDateBlock = AtomicBooleanProperty(false)
    private val limitOffsetBlock = AtomicBooleanProperty(false)
    private val filterPanelBlock = AtomicBooleanProperty(false)

    private val isEnabledAutoCommit = AtomicBooleanProperty(false)

    private val settingsPanelDelegate = lazy {
        val isConsumerSetup =
            (consumerGroup.editor.editorComponent as JTextField).enteredTextSatisfies { !it.trim().isEmpty() }

        val panel = panel {
            row(KafkaMessagesBundle.message("settings.label.topics")) {
                cell(topicComboBox).align(AlignX.FILL).resizableColumn()
            }

            key.createComponent(this)
            value.createComponent(this)

            group(KafkaMessagesBundle.message("settings.title.range.filters")) {
                row(KafkaMessagesBundle.message("settings.filters.from")) {
                    cell(startFromComboBox).align(AlignX.FILL).resizableColumn()
                }.enabledIf(isConsumerSetup.not())
                row {
                    comment(KafkaMessagesBundle.message("settings.filters.from.not.available.with.consumer.group")).visibleIf(
                        isConsumerSetup
                    )
                }.topGap(TopGap.NONE)

                row(KafkaMessagesBundle.message("consumer.timestamp.label")) {
                    cell(startSpecificDate).align(AlignX.FILL).resizableColumn()
                }.visibleIf(startSpecificDateBlock)
                row { cell(startOffset) }.visibleIf(startOffsetBlock)
                row {
                    cell(startConsumerGroup).align(AlignX.FILL).resizableColumn()
                }.visibleIf(startConsumerGroupBlock)

                row(KafkaMessagesBundle.message("settings.filters.limit")) {
                    cell(limitComboBox).align(AlignX.FILL).resizableColumn()
                }
                row(KafkaMessagesBundle.message("consumer.timestamp.label")) {
                    cell(limitSpecificDate).align(AlignX.FILL).resizableColumn()
                }.visibleIf(limitSpecificDateBlock)
                row { cell(limitOffset) }.visibleIf(limitOffsetBlock)

                row(KafkaMessagesBundle.message("settings.filter")) {
                    cell(filterComboBox).align(AlignX.FILL).resizableColumn()
                }
                row { cell(filterPanel).align(AlignX.FILL).resizableColumn() }.visibleIf(filterPanelBlock)
            }

            collapsibleGroup(KafkaMessagesBundle.message("settings.title.other")) {
                row(KafkaMessagesBundle.message("settings.partitions")) {
                    cell(partitionField).align(AlignX.FILL).resizableColumn().enabledIf(isConsumerSetup.not())
                }.bottomGap(BottomGap.NONE)
                row {
                    comment(KafkaMessagesBundle.message("settings.partitions.not.available.if.consumer.group.setup.comment"))
                }.topGap(TopGap.NONE).visibleIf(isConsumerSetup).bottomGap(BottomGap.NONE)

                row(KafkaMessagesBundle.message("settings.consumer.group.label")) {
                    cell(consumerGroup).align(AlignX.FILL).resizableColumn()
                }.topGap(TopGap.SMALL)
                row {
                    checkBox(KafkaMessagesBundle.message("settings.consumer.enable.auto.commit.label")).bindSelected(
                        isEnabledAutoCommit
                    )
                        .visibleIf(isConsumerSetup)
                }.bottomGap(BottomGap.SMALL)
                row {
                    link(KafkaMessagesBundle.message("task.change.offset")) {
                        KafkaConsumerGroupChangeOffsetProcess(project, kafkaManager, consumerGroup.item).showAndUpdate()
                    }
                }.topGap(TopGap.NONE).visibleIf(isConsumerSetup)
            }

            row { cell(advancedSettings) }
        }

        KafkaProducerConsumerPanel.createPanel(panel, consumeButton, progress)
    }

    private val settingsPanel: JPanel by settingsPanelDelegate

    private val presetsDelegate = lazy {
        val presets = ConsumerPresets()
        Disposer.register(this, presets)
        presets.onApply = { applyConfig(it) }
        presets.component.apply {
            minimumSize = Dimension(max(minimumSize.width, 290), minimumSize.height)
        }
        presets
    }

    private val presets: ConsumerPresets by presetsDelegate

    private val presetsSplitter = MultiSplitter()

    init {
        Disposer.register(this, consumerClient)

        restoreFromFile()

        presetsSplitter.proportionsKey = "kafka.consumer.multisplitter.proportions"
        presetsSplitter.add(
            ExpansionPanel(
                KafkaMessagesBundle.message("toggle.presets"),
                { presets.component },
                PRESETS_SHOW_ID,
                false
            )
        )
        presetsSplitter.add(
            ExpansionPanel(
                KafkaMessagesBundle.message("toggle.settings"), { settingsPanel },
                SETTINGS_SHOW_ID, true,
                listOf(SavePresetAction(KafkaConfigStorage.getInstance().consumerConfig) { getRunConfig() })
            )
        )
        presetsSplitter.add(output.dataPanel)
        presetsSplitter.add(output.detailsPanel)

        presetsSplitter.centralComponent = output.dataPanel

        updateVisibility()
        updateLimit()
        updateStartWith()
        updateFilter()

        storeToUserData()
    }

    override fun dispose() {
        storeToUserData()
    }

    private fun startConsume(project: Project?) {
        val runConfig = getRunConfig()

        if (runConfig.topic.isNullOrBlank()) {
            invokeLater {
                Messages.showErrorDialog(
                    kafkaManager.project,
                    KafkaMessagesBundle.message("consumer.error.topic.empty"),
                    KafkaMessagesBundle.message("consumer.error.topic.empty.title")
                )
            }
            return
        }

        try {
            output.start()

            if (kafkaConsumerSettingsDelegate.isInitialized()) {
                val maxElementsCount = kafkaConsumerSettings.getSettings()[KafkaConsumerSettings.MAX_CONSUMER_RECORDS]
                maxElementsCount?.toIntOrNull()?.let {
                    output.setMaxRows(it)
                } ?: output.setMaxRows(0)
            }

            withPluginClassLoader {
                // Callbacks called in Kafka client threads. That's why, to properly update UI we calling invokeLater
                consumerClient.start(
                    runConfig,
                    dataManager = kafkaManager,
                    keyConfig = key.loadFieldConfig(),
                    valueConfig = value.loadFieldConfig(),
                    consume = { pollTime, records ->
                        val convertedRecords = records.map {
                            KafkaRecord.createFor(
                                key.fieldTypeComboBox.item, value.fieldTypeComboBox.item,
                                key.schemaComboBox.item?.schemaFormat,
                                value.schemaComboBox.item?.schemaFormat,
                                Result.success(it)
                            )
                        }
                        if (convertedRecords.isEmpty())
                            return@start
                        invokeLater {
                            output.addBatchRows(pollTime, convertedRecords)
                        }
                    },
                    timestampUpdate = {
                        progress.onUpdate()

                    },
                    consumeError = { error, partition, offset ->
                        progress.onError()

                        val element = KafkaRecord.createFor(
                            key.fieldTypeComboBox.item, value.fieldTypeComboBox.item,
                            key.schemaComboBox.item?.schemaFormat,
                            value.schemaComboBox.item?.schemaFormat,
                            Result.failure(error),
                            errorPartition = partition,
                            errorOffset = offset
                        )
                        invokeLater {
                            output.addError(element)
                        }
                    })
            }
        } catch (t: Throwable) {
            onStopConsume()
            invokeLater {
                RfsNotificationUtils.showExceptionMessage(
                    project,
                    t,
                    KafkaMessagesBundle.message("error.start.consumer")
                )
            }
            return
        }
    }

    private fun getRunConfig(): StorageConsumerConfig {
        val topicName = topicComboBox.item?.name ?: ""
        val startWith = ConsumerEditorUtils.getStartWith(
            startFromComboBox.item,
            startOffset.text,
            startSpecificDate.getDateTime(),
            startConsumerGroup.item
        )
        val filter = getFilter()

        val consumerLimit = ConsumerLimit(
            limitComboBox.item, limitOffset.text,
            if (limitComboBox.item == ConsumerLimitType.DATE) limitSpecificDate.getDateTime()?.time else null
        )

        val (properties, settings) = if (kafkaConsumerSettingsDelegate.isInitialized()) {
            kafkaConsumerSettings.getProperties() to kafkaConsumerSettings.getSettings()
        } else {
            emptyMap<String, String>() to emptyMap()
        }

        return StorageConsumerConfig(
            topic = topicName,
            keyType = key.fieldTypeComboBox.item,
            keySubject = key.schemaComboBox.item?.schemaName ?: "",
            keyFormat = key.schemaComboBox.item?.schemaFormat ?: KafkaRegistryFormat.AVRO,

            valueType = value.fieldTypeComboBox.item,
            valueSubject = value.schemaComboBox.item?.schemaName ?: "",
            valueFormat = value.schemaComboBox.item?.schemaFormat ?: KafkaRegistryFormat.AVRO,

            partitions = partitionField.text,
            limit = consumerLimit,
            filter = filter,
            startWith = startWith,
            properties = properties,
            settings = settings,
            consumerGroup = consumerGroup.item.takeIf { it.isNotBlank() }
                ?.let { ConsumerGroup(it, isEnabledAutoCommit.get()) },

            customKeySchema = key.getCustomSchemaConfig(),
            customValueSchema = value.getCustomSchemaConfig()
        )
    }

    fun getComponent(): JComponent = presetsSplitter

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

        partitionField.isEnabled = isEnabled && consumerGroup.item.isEmpty()
        consumerGroup.isEnabled = isEnabled

        key.updateIsEnabled(isEnabled)
        value.updateIsEnabled(isEnabled)

        startFromComboBox.isEnabled = isEnabled && consumerGroup.item.isEmpty()
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
        startSpecificDateBlock.set(startFromComboBox.selectedItem == ConsumerStartType.SPECIFIC_DATE)
        startOffsetBlock.set(
            startFromComboBox.selectedItem == ConsumerStartType.OFFSET ||
                    startFromComboBox.selectedItem == ConsumerStartType.LATEST_OFFSET_MINUS_X
        )
        startConsumerGroupBlock.set(startFromComboBox.selectedItem == ConsumerStartType.CONSUMER_GROUP)
    }

    private fun updateFilter() {
        filterPanelBlock.set(filterComboBox.selectedItem != ConsumerFilterType.NONE)
    }

    private fun updateLimit() {
        limitSpecificDateBlock.set(false)
        limitOffsetBlock.set(false)

        when (limitComboBox.selectedItem) {
            ConsumerLimitType.DATE -> limitSpecificDateBlock.set(true)
            ConsumerLimitType.TOPIC_NUMBER_RECORDS,
            ConsumerLimitType.PARTITION_NUMBER_RECORDS,
            ConsumerLimitType.PARTITION_MAX_SIZE,
            ConsumerLimitType.TOPIC_MAX_SIZE -> limitOffsetBlock.set(true)
        }
    }

    private fun onStopConsume() = invokeLater {
        consumeButton.text = KafkaMessagesBundle.message("action.consume.start.title")
        consumeButton.icon = AllIcons.Actions.Execute

        progress.onStop()

        updateVisibility()
    }

    private fun onStartConsume() = invokeLater {
        consumeButton.text = KafkaMessagesBundle.message("action.consume.stop.title")
        consumeButton.icon = AllIcons.Actions.Suspend

        progress.onStart()
        updateVisibility()
    }

    private var isRestoring = false

    internal fun storeToUserData() {
        if (isRestoring)
            return
        file.putUserData(STATE_KEY, ConsumerEditorState(output.getElements(), getRunConfig()))
    }

    private fun restoreFromFile() {
        try {
            isRestoring = true

            val state = file.getUserData(STATE_KEY) ?: return
            output.replace(state.output)

            applyConfig(state.config)
        } finally {
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
        startSpecificDate.setDateTime(startWith.time)
        startConsumerGroup.item = startWith.consumerGroup ?: ""

        val limit = config.getLimit()
        limitComboBox.item = limit.type
        limitOffset.text = limit.value
        startSpecificDate.setDateTime(limit.time)

        val filter = config.getFilter()
        filterComboBox.item = filter.type
        filterKeyField.text = filter.filterKey
        filterValueField.text = filter.filterValue
        filterHeadKeyField.text = filter.filterHeadKey
        filterHeadValueField.text = filter.filterHeadValue

        partitionField.text = config.partitions

        kafkaConsumerSettings.applyConfig(config)
    }

    internal fun getRecords(): ListTableModel<KafkaRecord> = output.outputModel

    companion object {
        val STATE_KEY = Key<ConsumerEditorState>("STATE")

        // A number of string keys for PropertiesComponent.getInstance().getBoolean(**_**_ID, false)
        private const val SETTINGS_SHOW_ID = "io.confluent.intellijplugin.consumer.settings.show"
        private const val PRESETS_SHOW_ID = "io.confluent.intellijplugin.consumer.presets.show"
    }
}