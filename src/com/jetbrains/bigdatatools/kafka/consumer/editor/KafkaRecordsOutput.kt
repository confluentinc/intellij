package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.jetbrains.bigdatatools.common.table.MaterialTable
import com.jetbrains.bigdatatools.common.table.MaterialTableUtils
import com.jetbrains.bigdatatools.common.table.TableResizeController
import com.jetbrains.bigdatatools.common.table.extension.TableCellPreview
import com.jetbrains.bigdatatools.common.table.extension.TableFirstRowAdded
import com.jetbrains.bigdatatools.common.table.extension.TableLoadingDecorator
import com.jetbrains.bigdatatools.common.table.filters.TableFilterHeader
import com.jetbrains.bigdatatools.common.table.renderers.DateRenderer
import com.jetbrains.bigdatatools.common.table.renderers.DurationRenderer
import com.jetbrains.bigdatatools.common.ui.ExpansionPanel
import com.jetbrains.bigdatatools.common.ui.onDoubleClick
import com.jetbrains.bigdatatools.common.ui.setSouthComponent
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTable
import kotlin.math.max

class KafkaRecordsOutput(val project: Project, val isProducer: Boolean) : Disposable {
  private var tableLoadingDecorator: TableLoadingDecorator? = null

  private val outputModel = ListTableModel(LinkedList<KafkaRecord>(),
                                           listOf(TIMESTAMP_FIELD, KEY_COLUMN, VALUE_COLUMN, PARTITION_COLUMN) +
                                           if (isProducer) listOf(DURATION_COLUMN) else listOf(OFFSET_COLUMN)) { data, index ->
    when (index) {
      0 -> Date(data.timestamp)
      1 -> data.keyText ?: KafkaMessagesBundle.message("error.output.row.key")
      2 -> data.valueText ?: data.errorText
      3 -> data.partition
      4 -> if (isProducer) data.duration else data.offset
      else -> ""
    }
  }.apply {
    columnClasses = listOf(Date::class.java, String::class.java, String::class.java, Long::class.java, Long::class.java)
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

      TableFilterHeader(this)

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

  private val outputTablePanelDelegate = lazy {
    JPanel(BorderLayout()).apply {
      add(ScrollPaneFactory.createScrollPane(outputTable, true), BorderLayout.CENTER)
      setSouthComponent(statisticPanel.component)
      statisticPanel.component.isVisible = PropertiesComponent.getInstance().getBoolean(TABLE_STATS_ID, false)
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
    val clearButton = DumbAwareAction.create(KafkaMessagesBundle.message("action.clear.output"), AllIcons.Actions.GC) {
      outputModel.clear()
      if (outputTableDelegate.isInitialized()) {
        TableFirstRowAdded(outputTable) {
          MaterialTableUtils.fitColumnsWidth(outputTable)
          TableResizeController.getFor(outputTable)?.componentResized()
        }
      }
    }

    val tableStatusButton = object : DumbAwareToggleAction(KafkaMessagesBundle.message("action.table.stats"), null,
                                                           AllIcons.General.ShowInfos) {
      override fun isSelected(e: AnActionEvent) = statisticPanel.component.isVisible
      override fun getActionUpdateThread() = ActionUpdateThread.BGT
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        statisticPanel.component.isVisible = state
        PropertiesComponent.getInstance().setValue(TABLE_STATS_ID, state)
        outputTablePanel.revalidate()
      }
    }

    dataPanel = ExpansionPanel(KafkaMessagesBundle.message("toggle.data"), { outputTablePanel },
                               DATA_SHOW_ID, true,
                               listOf(tableStatusButton, clearButton))

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
        updateDetails()
      }
    }
  }

  override fun dispose() {}

  fun replace(output: List<KafkaRecord>) {
    outputModel.clear()
    output.forEach {
      outputModel.addElement(it)
    }
  }

  fun stop() {
    tableLoadingDecorator?.let { Disposer.dispose(it) }
  }

  fun start() {
    if (outputTableDelegate.isInitialized()) {
      tableLoadingDecorator?.let { Disposer.dispose(it) }
      tableLoadingDecorator = TableLoadingDecorator.installOn(outputTable,
                                                              this@KafkaRecordsOutput,
                                                              KafkaMessagesBundle.message("consumer.table.awaiting"))
    }

    statisticPanel.start()
  }

  fun setMaxRows(limit: Int) {
    outputModel.maxElementsCount = limit
  }

  fun addBatchRows(pollTime: Long, elements: List<KafkaRecord>) {
    elements.forEach {
      outputModel.addElement(it)
    }
    statisticPanel.addRecordsBatch(pollTime, elements)
  }

  fun addError(element: KafkaRecord) {
    outputModel.addElement(element)
  }

  fun getElements(): List<KafkaRecord> {
    return outputModel.elements().toList()
  }

  private fun setupTablePopupMenu(table: JTable) {
    val clearAction = DumbAwareAction.create(KafkaMessagesBundle.message("action.clear.output")) { outputModel.clear() }
    val openDetails = DumbAwareAction.create(KafkaMessagesBundle.message("action.open.details")) { detailsPanel.expanded = true }

    PopupHandler.installPopupMenu(table, DefaultActionGroup().apply {
      addAction(openDetails)
      addSeparator()
      (ActionManager.getInstance().getAction("BdIde.TableEditor.PopupActionGroup") as? ActionGroup)?.let { addAll(it) }
      addSeparator()
      addAction(clearAction)
    }, "KafkaRecordOutput")
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

  companion object {
    private val TIMESTAMP_FIELD = KafkaMessagesBundle.message("output.column.timestamp")
    private val KEY_COLUMN = KafkaMessagesBundle.message("output.column.key")
    private val VALUE_COLUMN = KafkaMessagesBundle.message("output.column.value")
    private val PARTITION_COLUMN = KafkaMessagesBundle.message("output.column.partition")
    private val OFFSET_COLUMN = KafkaMessagesBundle.message("output.column.offset")
    private val DURATION_COLUMN = KafkaMessagesBundle.message("output.column.duration")

    internal const val DATA_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.data.show"
    internal const val DETAILS_SHOW_ID = "com.jetbrains.bigdatatools.kafka.consumer.details.show"
    internal const val TABLE_STATS_ID = "com.jetbrains.bigdatatools.kafka.consumer.table.stats.show"
  }
}