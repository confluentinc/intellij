package io.confluent.intellijplugin.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import io.confluent.intellijplugin.common.editor.*
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.common.models.TopicInEditor
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.client.ConsumerClient
import io.confluent.intellijplugin.consumer.client.ConsumerClientProvider
import io.confluent.intellijplugin.consumer.models.*
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.core.ui.ExpansionPanel
import io.confluent.intellijplugin.core.ui.MultiSplitter
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.ClusterScopedDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

/**
 * Consumer panel for Confluent Cloud connections.
 *
 * Uses REST API for consuming records via [CCloudConsumerClient].
 * This is a simplified version compared to [KafkaConsumerPanel] since
 * CCloud connections don't support all native Kafka features like
 * consumer groups, schema registry, or advanced settings.
 */
class CCloudConsumerPanel(
    val project: Project,
    private val clusterDataManager: ClusterScopedDataManager,
    private val file: VirtualFile
) : Disposable {

    private val consumerClient: ConsumerClient = ConsumerClientProvider.getClient(
        dataManager = clusterDataManager,
        onStart = ::onStartConsume,
        onStop = ::onStopConsume
    )

    private val output = KafkaRecordsOutput(project, isProducer = false).also { Disposer.register(this, it) }

    private val startSpecificDate = TimestampTextField(this)
    private val limitSpecificDate = TimestampTextField(this)
    private val limitOffset = JBTextField(15)
    private val startOffset = JBTextField(15)

    // Only show start types supported by CCloud REST API (CONSUMER_GROUP requires server-side state)
    private val supportedStartTypes = listOf(
        ConsumerStartType.NOW,
        ConsumerStartType.THE_BEGINNING,
        ConsumerStartType.LAST_HOUR,
        ConsumerStartType.TODAY,
        ConsumerStartType.YESTERDAY,
        ConsumerStartType.SPECIFIC_DATE,
        ConsumerStartType.OFFSET,
        ConsumerStartType.LATEST_OFFSET_MINUS_X
    )

    private val startFromComboBox = ComboBox(supportedStartTypes.toTypedArray()).apply {
        renderer = CustomListCellRenderer<ConsumerStartType> { it.title }
        item = ConsumerStartType.NOW
        addActionListener {
            updateStartWith()
            getComponent().revalidate()
        }
    }

    private val limitComboBox = ComboBox(ConsumerLimitType.entries.toTypedArray()).apply {
        renderer = CustomListCellRenderer<ConsumerLimitType> { it.title }
        item = ConsumerLimitType.NONE
        addActionListener {
            updateLimit()
            getComponent().revalidate()
        }
    }

    private val filterComboBox = ComboBox(ConsumerFilterType.entries.toTypedArray()).apply {
        renderer = CustomListCellRenderer<ConsumerFilterType> { it.title }
        item = ConsumerFilterType.NONE
        addActionListener {
            updateFilter()
            getComponent().revalidate()
        }
    }

    private val progress = KafkaProducerConsumerProgressComponent()

    private val filterKeyField = JBTextField()
    private val filterValueField = JBTextField()
    private val filterHeadKeyField = JBTextField()
    private val filterHeadValueField = JBTextField()

    private val partitionField = JBTextField()

    val topicComboBox: ComboBox<TopicInEditor>

    private val consumeButton: JButton =
        JButton(KafkaMessagesBundle.message("action.consume.start.title"), AllIcons.Actions.Execute).apply {
            addActionListener {
                executeOnPooledThread {
                    try {
                        if (consumerClient.isRunning()) {
                            consumerClient.stop()
                            output.stop()
                        } else {
                            startConsume()
                        }
                        updateVisibility()
                        invalidate()
                        repaint()
                    } catch (t: Throwable) {
                        @Suppress("DialogTitleCapitalization")
                        RfsNotificationUtils.notifyException(t, KafkaMessagesBundle.message("error.start.consumer"))
                    }
                }
            }
        }

    private val startSpecificDateBlock = AtomicBooleanProperty(false)
    private val startOffsetBlock = AtomicBooleanProperty(false)
    private val limitSpecificDateBlock = AtomicBooleanProperty(false)
    private val limitOffsetBlock = AtomicBooleanProperty(false)
    private val filterPanelBlock = AtomicBooleanProperty(false)

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

    private val settingsPanelDelegate = lazy {
        val panel = panel {
            row(KafkaMessagesBundle.message("settings.label.topics")) {
                cell(topicComboBox).align(AlignX.FILL).resizableColumn()
            }

            group(KafkaMessagesBundle.message("settings.title.range.filters")) {
                row(KafkaMessagesBundle.message("settings.filters.from")) {
                    cell(startFromComboBox).align(AlignX.FILL).resizableColumn()
                }

                row(KafkaMessagesBundle.message("consumer.timestamp.label")) {
                    cell(startSpecificDate).align(AlignX.FILL).resizableColumn()
                }.visibleIf(startSpecificDateBlock)

                row { cell(startOffset) }.visibleIf(startOffsetBlock)

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
                    cell(partitionField).align(AlignX.FILL).resizableColumn()
                }.bottomGap(BottomGap.NONE)
            }
        }

        KafkaProducerConsumerPanel.createPanel(panel, consumeButton, progress)
    }

    private val settingsPanel by settingsPanelDelegate

    private val presetsSplitter = MultiSplitter()

    init {
        Disposer.register(this, consumerClient)

        // Trigger topic model refresh to populate topics
        clusterDataManager.updater.invokeRefreshModel(clusterDataManager.topicModel)

        topicComboBox = KafkaEditorUtils.createTopicComboBox(this, clusterDataManager).apply {
            prototypeDisplayValue = TopicInEditor("AverageName")
            addActionListener {
                FileEditorManager.getInstance(project).updateFilePresentation(file)
            }
        }

        presetsSplitter.proportionsKey = "kafka.ccloud.consumer.multisplitter.proportions"
        presetsSplitter.add(
            ExpansionPanel(
                KafkaMessagesBundle.message("toggle.settings"), { settingsPanel },
                SETTINGS_SHOW_ID, true, emptyList()
            )
        )
        presetsSplitter.add(output.dataPanel)

        updateVisibility()
    }

    private fun startConsume() {
        val runConfig = getRunConfig()
        if (runConfig.topic.isNullOrBlank()) {
            invokeLater {
                Messages.showErrorDialog(
                    project,
                    KafkaMessagesBundle.message("consumer.error.topic.empty"),
                    KafkaMessagesBundle.message("consumer.error.topic.empty.title")
                )
            }
            return
        }

        try {
            output.start()

            // Default field configs for CCloud (no schema registry support)
            val keyConfig = ConsumerProducerFieldConfig(
                type = KafkaFieldType.STRING,
                valueText = "",
                isKey = true,
                topic = topicComboBox.item?.name ?: "",
                registryType = KafkaRegistryType.NONE,
                schemaName = "",
                schemaFormat = KafkaRegistryFormat.UNKNOWN,
                parsedSchema = null
            )
            val valueConfig = ConsumerProducerFieldConfig(
                type = KafkaFieldType.JSON,
                valueText = "",
                isKey = false,
                topic = topicComboBox.item?.name ?: "",
                registryType = KafkaRegistryType.NONE,
                schemaName = "",
                schemaFormat = KafkaRegistryFormat.UNKNOWN,
                parsedSchema = null
            )

            consumerClient.start(
                runConfig,
                keyConfig = keyConfig,
                valueConfig = valueConfig,
                consume = { pollTime, records ->
                    val convertedRecords = records.map {
                        KafkaRecord.createFor(
                            KafkaFieldType.STRING, KafkaFieldType.JSON,
                            null, null,
                            Result.success(it)
                        )
                    }
                    if (convertedRecords.isEmpty()) return@start
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
                        KafkaFieldType.STRING, KafkaFieldType.JSON,
                        null, null,
                        Result.failure(error),
                        errorPartition = partition,
                        errorOffset = offset
                    )
                    invokeLater {
                        output.addError(element)
                    }
                }
            )
        } catch (t: Throwable) {
            onStopConsume()
            invokeLater {
                RfsNotificationUtils.showExceptionMessage(
                    project,
                    t,
                    KafkaMessagesBundle.message("error.start.consumer")
                )
            }
        }
    }

    private fun getRunConfig(): StorageConsumerConfig {
        val topicName = topicComboBox.item?.name ?: ""

        val startWith = ConsumerEditorUtils.getStartWith(
            startFromComboBox.item,
            startOffset.text,
            startSpecificDate.getDateTime(),
            null // No consumer group for CCloud
        )
        val filter = getFilter()

        val consumerLimit = ConsumerLimit(
            limitComboBox.item,
            limitOffset.text,
            if (limitComboBox.item == ConsumerLimitType.DATE) limitSpecificDate.getDateTime()?.time else null
        )

        return StorageConsumerConfig(
            topic = topicName,
            keyType = KafkaFieldType.STRING,
            keySubject = "",
            keyFormat = KafkaRegistryFormat.UNKNOWN,
            valueType = KafkaFieldType.JSON,
            valueSubject = "",
            valueFormat = KafkaRegistryFormat.UNKNOWN,
            partitions = partitionField.text,
            limit = consumerLimit,
            filter = filter,
            startWith = startWith,
            properties = emptyMap(),
            settings = emptyMap(),
            consumerGroup = null,
            customKeySchema = null,
            customValueSchema = null
        )
    }

    private fun getFilter() = ConsumerFilter(
        type = filterComboBox.item,
        filterKey = filterKeyField.text.ifBlank { null },
        filterValue = filterValueField.text.ifBlank { null },
        filterHeadKey = filterHeadKeyField.text.ifBlank { null },
        filterHeadValue = filterHeadValueField.text.ifBlank { null }
    )

    internal fun updateVisibility() {
        val isEnabled = !consumerClient.isRunning()

        consumeButton.text = if (isEnabled)
            KafkaMessagesBundle.message("action.consume.start.title")
        else
            KafkaMessagesBundle.message("action.consume.stop.title")

        consumeButton.icon = if (isEnabled)
            AllIcons.Actions.Execute
        else
            AllIcons.Actions.Suspend

        topicComboBox.isEnabled = isEnabled
        partitionField.isEnabled = isEnabled
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
        startSpecificDateBlock.set(startFromComboBox.selectedItem == ConsumerStartType.SPECIFIC_DATE)
        startOffsetBlock.set(
            startFromComboBox.selectedItem == ConsumerStartType.OFFSET ||
            startFromComboBox.selectedItem == ConsumerStartType.LATEST_OFFSET_MINUS_X
        )
    }

    private fun updateLimit() {
        limitSpecificDateBlock.set(false)
        limitOffsetBlock.set(false)

        when (limitComboBox.selectedItem) {
            ConsumerLimitType.DATE -> limitSpecificDateBlock.set(true)
            ConsumerLimitType.TOPIC_NUMBER_RECORDS,
            ConsumerLimitType.PARTITION_NUMBER_RECORDS,
            ConsumerLimitType.TOPIC_MAX_SIZE,
            ConsumerLimitType.PARTITION_MAX_SIZE -> limitOffsetBlock.set(true)
            else -> {}
        }
    }

    private fun updateFilter() {
        filterPanelBlock.set(filterComboBox.selectedItem != ConsumerFilterType.NONE)
    }

    private fun onStartConsume() {
        updateVisibility()
        progress.onStart()
    }

    private fun onStopConsume() {
        updateVisibility()
        progress.onStop()
    }

    fun getComponent(): JComponent = presetsSplitter

    override fun dispose() {
        consumerClient.stop()
    }

    companion object {
        const val SETTINGS_SHOW_ID = "kafka.consumer.ccloud.settings.show"
    }
}
