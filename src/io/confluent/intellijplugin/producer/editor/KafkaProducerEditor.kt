package io.confluent.intellijplugin.producer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import io.confluent.intellijplugin.common.editor.*
import io.confluent.intellijplugin.common.models.TopicInEditor
import io.confluent.intellijplugin.common.settings.KafkaConfigStorage
import io.confluent.intellijplugin.common.settings.StorageProducerConfig
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.editor.KafkaRecordsOutput
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.getValidationInfo
import io.confluent.intellijplugin.core.rfs.driver.SafeExecutor
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.core.ui.ExpansionPanel
import io.confluent.intellijplugin.core.ui.MultiSplitter
import io.confluent.intellijplugin.core.util.executeNotOnEdtSuspend
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.producer.client.ProducerClient
import io.confluent.intellijplugin.producer.client.ProducerClientProvider
import io.confluent.intellijplugin.producer.models.AcksType
import io.confluent.intellijplugin.producer.models.Mode
import io.confluent.intellijplugin.producer.models.ProducerEditorState
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.producer.models.RecordCompression
import io.confluent.intellijplugin.telemetry.MessageViewerEvent
import io.confluent.intellijplugin.telemetry.logUsage
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

class KafkaProducerEditor(
    val project: Project,
    internal val kafkaManager: BaseClusterDataManager,
    private val file: VirtualFile,
    topic: String?
) : FileEditor, UserDataHolderBase() {
    private var isRestoring = false

    private val isNativeConnection = kafkaManager is KafkaDataManager

    private val output = KafkaRecordsOutput(project, isProducer = true).also { Disposer.register(this, it) }

    private val flowController = KafkaFlowController(project)
    private val progress = KafkaProducerConsumerProgressComponent()

    private val producerClient: ProducerClient = ProducerClientProvider.getClient(
        dataManager = kafkaManager,
        onStart = ::onStart,
        onStop = ::onStop
    ).also {
        Disposer.register(this, it)
    }
    val topics = kafkaManager.getTopics()

    private val propertiesComponent = PropertiesTable("app.name=IntellijKafkaPlugin")

    val topicComboBox = KafkaEditorUtils.createTopicComboBox(this, kafkaManager).apply {
        addActionListener {
            storeToFile()
            FileEditorManager.getInstance(project).updateFilePresentation(file)
        }
    }

    private lateinit var acksComboBox: SegmentedButton<AcksType>

    private val idempotenceCheckBox = CheckBox(KafkaMessagesBundle.message("producer.idempotence.label"))

    private val compressionComboBox = ComboBox(RecordCompression.entries.toTypedArray()).apply {
        renderer =
            CustomListCellRenderer<RecordCompression> { StringUtil.wordsToBeginFromUpperCase(it.name.lowercase()) }
        selectedIndex = 0
    }

    private val keyFieldComponent = KafkaProducerFieldComponent(this, isKey = true).also { Disposer.register(this, it) }
    private val valueFieldComponent =
        KafkaProducerFieldComponent(this, isKey = false).also { Disposer.register(this, it) }

    private val forcePartitionField = IntegerField().apply {
        isCanBeEmpty = true
        defaultValue = -1
        emptyText.text = KafkaMessagesBundle.message("producer.forcePartition.emptytext")
    }

    private val presetsDelegate = lazy {
        val presets = ProducerPresets()
        Disposer.register(this, presets)
        presets.onApply = { applyConfig(it) }
        presets.component.apply {
            minimumSize = Dimension(max(minimumSize.width, 200), minimumSize.height)
        }
        presets
    }

    private val presets: ProducerPresets by presetsDelegate

    private val produceButton =
        JButton(KafkaMessagesBundle.message("kafka.producer.action.produce.title"), AllIcons.Actions.Execute).also {
            it.addActionListener {
                if (producerClient.isRunning())
                    stopProduce()
                else
                    startProduce()
            }
        }

    private val settingsPanelDelegate = lazy {
        val panel = panel {
            row(KafkaMessagesBundle.message("producer.topics")) {
                cell(topicComboBox).align(AlignX.FILL).resizableColumn()
            }

            keyFieldComponent.createComponent(this)
            valueFieldComponent.createComponent(this)

            collapsibleGroup(KafkaMessagesBundle.message("producer.title.headers")) {
                row {
                    cell(propertiesComponent.getComponent()).align(AlignX.FILL).resizableColumn().comment(
                        KafkaMessagesBundle.message("text.pasting.json.or.csv.available")
                    )
                }

            }.topGap(TopGap.NONE).bottomGap(BottomGap.NONE)

            flowController.createComponent(this)


            flowController.generateRandomKeys.onChanged {
                keyFieldComponent.randomGenerationEnabled.set(it.model.isSelected)
            }

            flowController.generateRandomValues.onChanged {
                valueFieldComponent.randomGenerationEnabled.set(it.model.isSelected)
            }

            collapsibleGroup(KafkaMessagesBundle.message("producer.title.options")) {
                row(KafkaMessagesBundle.message("producer.forcePartition")) {
                    cell(forcePartitionField).align(AlignX.FILL).resizableColumn()
                }
                if (isNativeConnection) {
                    row(KafkaMessagesBundle.message("producer.compression")) {
                        cell(compressionComboBox).align(AlignX.FILL).resizableColumn()
                    }
                    row {
                        cell(idempotenceCheckBox).align(AlignX.FILL).resizableColumn().comment(
                            KafkaMessagesBundle.message("producer.idempotence.comment")
                        )
                    }
                    row(KafkaMessagesBundle.message("producer.asks")) {
                        acksComboBox = segmentedButton(AcksType.entries) {
                            text = StringUtil.wordsToBeginFromUpperCase(it.name.lowercase())
                        }
                        acksComboBox.selectedItem = AcksType.NONE
                    }.visibleIf(idempotenceCheckBox.selected.not())
                } else {
                    // Initialize acksComboBox even when hidden (referenced in getConfig/applyConfig)
                    row {
                        acksComboBox = segmentedButton(AcksType.entries) {
                            text = StringUtil.wordsToBeginFromUpperCase(it.name.lowercase())
                        }
                        acksComboBox.selectedItem = AcksType.ALL
                    }.visible(false)
                }
            }.topGap(TopGap.NONE)
        }

        KafkaProducerConsumerPanel.createPanel(panel, produceButton, progress)
    }

    private val settingsPanel: JPanel by settingsPanelDelegate

    private val presetsSplitter = MultiSplitter()

    init {
        presetsSplitter.proportionsKey = "kafka.producer.multisplitter.proportions"
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
                KafkaMessagesBundle.message("toggle.configuration"), { settingsPanel },
                SETTINGS_SHOW_ID, true,
                listOf(
                    SavePresetAction(
                        KafkaConfigStorage.getInstance().producerConfig
                    ) { getConfig() })
            )
        )
        presetsSplitter.add(output.dataPanel)
        presetsSplitter.add(output.detailsPanel)

        presetsSplitter.centralComponent = output.dataPanel

        restoreFromFile()

        topic?.let { topicComboBox.item = TopicInEditor(it) }
    }

    private fun stopProduce() {
        producerClient.stop()
    }

    private fun startProduce(): Boolean {
        val topic = topicComboBox.item

        val validationInfo = topicComboBox.getValidationInfo()
            ?: keyFieldComponent.getValidationInfo()?.takeIf { !flowController.getParams().generateRandomKeys }
            ?: valueFieldComponent.getValidationInfo()?.takeIf { !flowController.getParams().generateRandomValues }
            ?: keyFieldComponent.getSchemaValidationInfo()
            ?: valueFieldComponent.getSchemaValidationInfo()

        if (validationInfo != null) {
            progress.onValidationError()
            return true
        }

        val selectedTopicName = topic.name

        SafeExecutor.instance.coroutineScope.launch {
            try {
                executeNotOnEdtSuspend {
                    if (!flowController.getParams().generateRandomKeys && !keyFieldComponent.validateSchema())
                        return@executeNotOnEdtSuspend
                    if (!flowController.getParams().generateRandomKeys && !valueFieldComponent.validateSchema())
                        return@executeNotOnEdtSuspend


                    val key = keyFieldComponent.getProducerField()
                    val value = valueFieldComponent.getProducerField()

                    producerClient.start(
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
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            }
        }

        return false
    }

    private fun onStart() = invokeLater {
        val config = getConfig()
        logUsage(
            MessageViewerEvent.StartProducer(
                keyType = config.keyType.lowercase(),
                valueType = config.valueType.lowercase(),
                autoModeEnabled = config.flowParams?.mode == Mode.AUTO,
                generateRandomKeys = config.flowParams?.generateRandomKeys == true,
                generateRandomValues = config.flowParams?.generateRandomValues == true,
                loadFromCsv = !config.flowParams?.csvFile.isNullOrBlank(),
                hasPartitionsSet = config.forcePartition >= 0,
                compressionType = config.compression.lowercase(),
                acks = config.acks.lowercase(),
                idempotence = config.idempotence,
            )
        )

        produceButton.text = KafkaMessagesBundle.message("action.produce.stop")
        produceButton.icon = AllIcons.Actions.Suspend
        progress.onStart()
        output.start()
    }

    private fun onStop() = invokeLater {
        logUsage(
            MessageViewerEvent.Stop(
                source = MessageViewerEvent.Source.PRODUCER,
                durationMs = output.getElapsedTimeMs(),
            )
        )

        produceButton.text = KafkaMessagesBundle.message("kafka.producer.action.produce.title")
        produceButton.icon = AllIcons.Actions.Execute
        progress.onStop()
        output.stop()
    }

    override fun dispose() {
        storeToFile()
    }

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
        } finally {
            isRestoring = false
        }
    }

    private fun getConfig() = StorageProducerConfig(
        topic = topicComboBox.item?.name ?: "",

        keyType = keyFieldComponent.fieldTypeComboBox.item?.name ?: "",
        key = keyFieldComponent.getValueText(),
        keyFormat = keyFieldComponent.schemaComboBox.item?.schemaFormat?.toString() ?: "",
        keySubject = keyFieldComponent.schemaComboBox.item?.schemaName ?: "",

        valueType = valueFieldComponent.fieldTypeComboBox.item?.name ?: "",
        value = valueFieldComponent.getValueText(),
        valueFormat = valueFieldComponent.schemaComboBox.item?.schemaFormat?.toString() ?: "",
        valueSubject = valueFieldComponent.schemaComboBox.item?.schemaName ?: "",

        properties = propertiesComponent.properties,
        compression = compressionComboBox.item?.name ?: "",
        acks = acksComboBox.selectedItem?.name ?: AcksType.NONE.name,
        idempotence = idempotenceCheckBox.isSelected,
        forcePartition = forcePartitionField.value,
        flowParams = flowController.getParams(),

        customKeySchema = keyFieldComponent.getCustomSchemaConfig(),
        customValueSchema = valueFieldComponent.getCustomSchemaConfig()
    )

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

    internal fun getRecords(): ListTableModel<KafkaRecord> = output.outputModel

    override fun getName(): String = KafkaMessagesBundle.message("produce.to.topic")
    override fun getComponent(): JComponent = presetsSplitter
    override fun getPreferredFocusedComponent(): JComponent = presetsSplitter
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    companion object {
        val STATE_KEY = Key<ProducerEditorState>("PRODUCER_STATE")

        private const val SETTINGS_SHOW_ID = "io.confluent.intellijplugin.producer.settings.show"
        private const val PRESETS_SHOW_ID = "io.confluent.intellijplugin.producer.presets.show"
    }
}