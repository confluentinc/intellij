package io.confluent.intellijplugin.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.consumer.search.SearchBarController
import io.confluent.intellijplugin.core.table.MaterialTable
import io.confluent.intellijplugin.core.table.MaterialTableUtils
import io.confluent.intellijplugin.core.table.extension.TableCellPreview
import io.confluent.intellijplugin.core.table.extension.TableFirstRowAdded
import io.confluent.intellijplugin.core.table.extension.TableLoadingDecorator
import io.confluent.intellijplugin.core.table.extension.TableResizeController
import io.confluent.intellijplugin.core.table.filters.TableFilterHeader
import io.confluent.intellijplugin.core.table.renderers.DateRenderer
import io.confluent.intellijplugin.core.table.renderers.DurationRenderer
import io.confluent.intellijplugin.core.ui.ExpansionPanel
import io.confluent.intellijplugin.core.ui.onDoubleClick
import io.confluent.intellijplugin.core.ui.setSouthComponent
import io.confluent.intellijplugin.telemetry.MessageViewerEvent
import io.confluent.intellijplugin.telemetry.logUsage
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import kotlin.math.max

class KafkaRecordsOutput(val project: Project, val isProducer: Boolean) : Disposable {
    private var tableLoadingDecorator: TableLoadingDecorator? = null
    private var filterHeader: TableFilterHeader? = null

    internal val outputModel = ListTableModel(
        ArrayDeque<KafkaRecord>(1000),
        listOf(TOPIC_FIELD, TIMESTAMP_FIELD, KEY_COLUMN, VALUE_COLUMN, PARTITION_COLUMN) +
                if (isProducer) listOf(DURATION_COLUMN) else listOf(OFFSET_COLUMN)
    ) { data, index ->
        when (index) {
            0 -> data.topic
            1 -> Date(data.timestamp)
            2 -> data.keyText ?: KafkaMessagesBundle.message("error.output.row.key")
            3 -> data.valueText ?: data.errorText
            4 -> data.partition
            5 -> if (isProducer) data.duration else data.offset
            else -> ""
        }
    }.apply {
        columnClasses = listOf(
            String::class.java,
            Date::class.java,
            String::class.java,
            String::class.java,
            Int::class.java,
            Long::class.java
        )
    }

    private val outputTableDelegate = lazy {
        MaterialTable(outputModel, outputModel.columnModel).apply {
            background = JBColor.WHITE
            tableHeader.background = JBColor.WHITE

            tableHeader.border = BorderFactory.createEmptyBorder()
            outputModel.columnModel.columns.asIterator().forEach {
                when (it.headerValue) {
                    TIMESTAMP_FIELD -> it.cellRenderer = DateRenderer()
                    DURATION_COLUMN -> it.cellRenderer = DurationRenderer()
                }
            }

            this.onDoubleClick {
                detailsPanel.expanded = !detailsPanel.expanded
            }

            MaterialTableUtils.setupSorters(this)
            TableFilterHeader(this).apply {
                externalFilterMode = true
                filterHeader = this
                setupFilterTelemetry(this)
            }

            val resizeController = TableResizeController.installOn(this).apply {
                setResizePriorityList(VALUE_COLUMN)
                mode = TableResizeController.Mode.PRIOR_COLUMNS_LIST
            }

            MaterialTableUtils.fitColumnsWidth(this)
            resizeController.componentResized()

            TableFirstRowAdded(this) {
                MaterialTableUtils.fitColumnsWidth(this)
                resizeController.componentResized()
            }

            setupTablePopupMenu(this)

            TableCellPreview.installOn(this, listOf(KEY_COLUMN, VALUE_COLUMN))
        }
    }

    private val outputTable: MaterialTable by outputTableDelegate

    private val searchController: SearchBarController by lazy {
        SearchBarController(this, outputTable, filterHeader!!, isProducer)
    }

    private val searchField get() = searchController.searchField

    private val searchAction = object : DumbAwareAction(), CustomComponentAction {
        override fun actionPerformed(e: AnActionEvent) {}

        override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
            return object : JPanel(BorderLayout()) {
                init {
                    isOpaque = false
                    add(searchField, BorderLayout.CENTER)
                }

                override fun getPreferredSize(): Dimension {
                    val base = searchField.preferredSize
                    val toolbarComponent = parent ?: return base
                    val titlePanel = toolbarComponent.parent ?: return base
                    if (titlePanel.width <= 0) return base
                    val titleLabelWidth = titlePanel.components
                        .filter { it !== toolbarComponent }
                        .sumOf { it.preferredSize.width }
                    val otherToolbarItemsWidth = toolbarComponent.components
                        .filter { it !== this }
                        .sumOf { it.preferredSize.width }
                    val insets = titlePanel.insets.let { it.left + it.right }
                    val available = titlePanel.width - titleLabelWidth - otherToolbarItemsWidth - insets - JBUI.scale(SEARCH_MARGIN)
                    return Dimension(max(JBUI.scale(SEARCH_MIN_WIDTH), available), base.height)
                }
            }
        }
    }

    private val outputTablePanelDelegate = lazy {
        JPanel(BorderLayout()).apply {
            add(ScrollPaneFactory.createScrollPane(outputTable, true), BorderLayout.CENTER)
            statisticPanel.toolbar.targetComponent = outputTable
            setSouthComponent(statisticPanel.toolbar.component)
        }
    }
    private val outputTablePanel: JPanel by outputTablePanelDelegate

    private val statisticPanel = ConsumerTableStats()

    private val detailsDelegate: Lazy<KafkaRecordDetails> = lazy {
        KafkaRecordDetails(project, this)
    }

    private val details: KafkaRecordDetails by detailsDelegate

    val dataPanel: ExpansionPanel
    val detailsPanel: ExpansionPanel

    init {
        val clearButton =
            DumbAwareAction.create(KafkaMessagesBundle.message("action.clear.output"), AllIcons.Actions.GC) {
                outputModel.clear()
                if (outputTableDelegate.isInitialized()) {
                    TableFirstRowAdded(outputTable) {
                        MaterialTableUtils.fitColumnsWidth(outputTable)
                        TableResizeController.getFor(outputTable)?.componentResized()
                    }
                }
            }

        dataPanel = ExpansionPanel(
            KafkaMessagesBundle.message("toggle.data"), { outputTablePanel },
            DATA_SHOW_ID, true,
            listOf(searchAction, ActionManager.getInstance().getAction("Kafka.ExportRecords.Actions"), clearButton),
            showTitle = false
        )

        detailsPanel = ExpansionPanel(KafkaMessagesBundle.message("toggle.details"), {
            details.component.apply {
                minimumSize = Dimension(max(details.component.minimumSize.width, 250), minimumSize.height)
            }
        }, DETAILS_SHOW_ID, false).apply {
            addChangeListener {
                if (expanded) {
                    updateDetails()
                }
            }
        }

        outputTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                if (outputTable.selectedRow != -1) {
                    logUsage(MessageViewerEvent.Preview(source = if (isProducer) MessageViewerEvent.Source.PRODUCER else MessageViewerEvent.Source.CONSUMER))
                }
                updateDetails()
            }
        }
    }

    override fun dispose() {}

    fun replace(output: List<KafkaRecord>) {
        outputModel.replaceAll(output)
    }

    fun stop() {
        tableLoadingDecorator?.let { Disposer.dispose(it) }
    }

    fun start() {
        if (outputTableDelegate.isInitialized()) {
            tableLoadingDecorator?.let { Disposer.dispose(it) }
            tableLoadingDecorator = TableLoadingDecorator.installOn(
                outputTable,
                this@KafkaRecordsOutput,
                KafkaMessagesBundle.message("consumer.table.awaiting")
            )
        }

        statisticPanel.start()
    }

    fun setMaxRows(limit: Int) {
        outputModel.maxElementsCount = limit
    }

    fun addBatchRows(pollTime: Long, elements: List<KafkaRecord>) {
        outputModel.addBatch(elements)
        statisticPanel.addRecordsBatch(pollTime, elements)
    }

    fun addError(element: KafkaRecord) {
        outputModel.addBatch(listOf(element))
    }

    fun getElapsedTimeMs(): Long = statisticPanel.getElapsedTimeMs()

    fun getElements(): List<KafkaRecord> {
        return outputModel.elements().toList()
    }

    private fun setupTablePopupMenu(table: JTable) {
        val clearAction =
            DumbAwareAction.create(KafkaMessagesBundle.message("action.clear.output")) { outputModel.clear() }
        val openDetails =
            DumbAwareAction.create(KafkaMessagesBundle.message("action.open.details")) { detailsPanel.expanded = true }

        PopupHandler.installPopupMenu(table, DefaultActionGroup().apply {
            addAll(getPopupTableActions())
            addSeparator()
            addAction(openDetails)
            addAction(clearAction)
        }, "KafkaRecordOutput")
    }

    private fun getPopupTableActions(): List<AnAction> {
        // Get actions from BdIde.TableEditor.PopupActionGroup, except BdiCopyHeaderAction and BdiTableDumpToFileAction
        val manager = ActionManager.getInstance()
        return listOf(
            manager.getAction("\$Copy"),
            manager.getAction("Kafka.BdiTableCopyRowAction"),
            manager.getAction("Kafka.BdiTableCopyColumnAction"),
            manager.getAction("Kafka.BdiTableDumpToClipboardAction")
        )
    }

    private fun updateDetails() {
        if (detailsDelegate.isInitialized()) {
            val row = if (outputTable.selectedRow == -1)
                null
            else
                outputModel.getValueAt(outputTable.convertRowIndexToModel(outputTable.selectedRow))
            details.update(row)
        }
    }

    private fun setupFilterTelemetry(filterHeader: TableFilterHeader) {
        val source = if (isProducer) MessageViewerEvent.Source.PRODUCER else MessageViewerEvent.Source.CONSUMER
        val attach = { controller: TableFilterHeader.FilterColumnsControllerPanel ->
            controller.forEach { editor ->
                var wasEmpty = true
                editor.addListener {
                    val isEmpty = editor.text.isNullOrBlank()
                    if (wasEmpty && !isEmpty) {
                        logUsage(MessageViewerEvent.Search(source))
                    }
                    wasEmpty = isEmpty
                }
            }
        }
        filterHeader.columnsController?.let(attach)
        filterHeader.addControllerRecreatedListener(attach)
    }

    companion object {
        private val TOPIC_FIELD = KafkaMessagesBundle.message("output.column.topic")
        private val TIMESTAMP_FIELD = KafkaMessagesBundle.message("output.column.timestamp")
        private val KEY_COLUMN = KafkaMessagesBundle.message("output.column.key")
        private val VALUE_COLUMN = KafkaMessagesBundle.message("output.column.value")
        private val PARTITION_COLUMN = KafkaMessagesBundle.message("output.column.partition")
        private val OFFSET_COLUMN = KafkaMessagesBundle.message("output.column.offset")
        private val DURATION_COLUMN = KafkaMessagesBundle.message("output.column.duration")

        private const val SEARCH_MARGIN = 40
        private const val SEARCH_MIN_WIDTH = 120

        internal const val DATA_SHOW_ID = "io.confluent.intellijplugin.consumer.data.show"
        internal const val DETAILS_SHOW_ID = "io.confluent.intellijplugin.consumer.details.show"
    }
}