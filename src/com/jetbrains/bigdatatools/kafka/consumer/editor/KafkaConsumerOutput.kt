package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.common.table.MaterialTable
import com.jetbrains.bigdatatools.common.table.MaterialTableUtils
import com.jetbrains.bigdatatools.common.table.extension.TableCellPreview
import com.jetbrains.bigdatatools.common.table.extension.TableFirstRowAdded
import com.jetbrains.bigdatatools.common.table.extension.TableLoadingDecorator
import com.jetbrains.bigdatatools.common.table.filters.TableFilterHeader
import com.jetbrains.bigdatatools.common.table.renderers.DateRenderer
import com.jetbrains.bigdatatools.common.ui.ExpansionPanel
import com.jetbrains.bigdatatools.common.ui.SimpleDumbAwareAction
import com.jetbrains.bigdatatools.common.ui.removeSouthComponent
import com.jetbrains.bigdatatools.common.ui.setSouthComponent
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorUtils
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTable
import kotlin.math.max

class KafkaConsumerOutput(val project: Project) : Disposable {
  private var tableLoadingDecorator: TableLoadingDecorator? = null

  private val outputModel = ListTableModel(LinkedList<ConsumerOutputRow>(),
                                           listOf("partition", "offset", "timestamp", "key", "value")) { data, index ->
    if (data.record.isFailure) {
      when (index) {
        3 -> "Error"
        4 -> data.record.exceptionOrNull()?.message ?: ""
        else -> null
      }
    }
    else {
      when (index) {
        0 -> data.record.getOrNull()?.partition()
        1 -> data.record.getOrNull()?.offset()
        2 -> data.record.getOrNull()?.let { Date(it.timestamp()) }
        3 -> KafkaEditorUtils.getValueAsString(data.keyType, data.record.getOrNull()?.key())
        4 -> KafkaEditorUtils.getValueAsString(data.valueType, data.record.getOrNull()?.value())
        else -> ""
      }
    }
  }.apply {
    columnClasses = listOf(Int::class.java, Long::class.java, Date::class.java, Object::class.java, Object::class.java)
  }

  private val outputTableDelegate = lazy {
    MaterialTable(outputModel, outputModel.columnModel).apply {
      background = JBColor.WHITE
      tableHeader.background = JBColor.WHITE

      tableHeader.border = BorderFactory.createEmptyBorder()
      outputModel.columnModel.columns.asIterator().forEach {
        if (it.headerValue == "timestamp") {
          it.cellRenderer = DateRenderer()
        }
      }

      TableFilterHeader(this)

      MaterialTableUtils.fitColumnsWidth(this)

      TableFirstRowAdded(this) {
        MaterialTableUtils.fitColumnsWidth(this)
      }

      setupTablePopupMenu(this)

      TableCellPreview.installOn(this, listOf("key", "value"))
    }
  }

  private val outputTable: MaterialTable by outputTableDelegate

  private val outputTablePanelDelegate = lazy {
    JPanel(BorderLayout()).apply {
      add(JBScrollPane(outputTable).apply {
        border = BorderFactory.createEmptyBorder()
      }, BorderLayout.CENTER)
      if (PropertiesComponent.getInstance().getBoolean(KafkaConsumerPanel.TABLE_STATS_ID, false)) {
        setSouthComponent(outputTableStatus.component)
      }
    }
  }
  private val outputTablePanel: JPanel by outputTablePanelDelegate

  private val outputTableStatusDelegate = lazy {
    ConsumerTableStats().apply {
      setModel(outputTable, outputModel)
    }
  }
  private val outputTableStatus: ConsumerTableStats by outputTableStatusDelegate


  private val detailsDelegate: Lazy<ConsumerRecordDetails> = lazy {
    ConsumerRecordDetails(project, this)
  }

  private val details: ConsumerRecordDetails by detailsDelegate


  internal val resultsSplitter = OnePixelSplitter().apply {
    lackOfSpaceStrategy = Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE
    dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_SECOND_SIZE
  }

  init {
    val dataExpanded = PropertiesComponent.getInstance().getBoolean(KafkaConsumerPanel.DATA_SHOW_ID, true)

    val clearButton = SimpleDumbAwareAction(KafkaMessagesBundle.message("action.clear.output"), AllIcons.Actions.GC) {
      outputModel.clear()
    }


    val tableStatusButton = object : DumbAwareToggleAction(KafkaMessagesBundle.message("action.table.stats"), null,
                                                           AllIcons.General.ShowInfos) {
      override fun isSelected(e: AnActionEvent) = outputTableStatusDelegate.isInitialized() && outputTableStatus.component.parent != null
      override fun getActionUpdateThread() = ActionUpdateThread.BGT
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) {
          outputTablePanel.setSouthComponent(outputTableStatus.component)
        }
        else {
          outputTablePanel.removeSouthComponent()
        }
        PropertiesComponent.getInstance().setValue(KafkaConsumerPanel.TABLE_STATS_ID, state)
        outputTablePanel.revalidate()
      }
    }

    resultsSplitter.firstComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.data"),
                                                    { outputTablePanel },
                                                    dataExpanded,
                                                    listOf(tableStatusButton, clearButton)
    ).apply {
      expandedServiceKey = KafkaConsumerPanel.DATA_SHOW_ID
      addChangeListener {
        resultsSplitter.proportion = if (this.expanded) 1f else 0.0001f
        resultsSplitter.setResizeEnabled(this.expanded)
      }
    }

    resultsSplitter.secondComponent = ExpansionPanel(KafkaMessagesBundle.message("toggle.details"), {
      details.component.apply {
        minimumSize = Dimension(max(details.component.minimumSize.width, 250), minimumSize.height)
      }
    }, PropertiesComponent.getInstance().getBoolean(KafkaConsumerPanel.DETAILS_SHOW_ID, false)).apply {
      expandedServiceKey = KafkaConsumerPanel.DETAILS_SHOW_ID
      addChangeListener {
        resultsSplitter.proportion = 1f
        if (this.expanded) {
          updateDetails()
        }
      }
    }
    resultsSplitter.proportion = if (dataExpanded) 1f else 0.0001f
    resultsSplitter.setResizeEnabled(dataExpanded)


    outputTable.selectionModel.addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        updateDetails()
      }
    }
  }

  override fun dispose() {}

  fun replace(output: List<ConsumerOutputRow>) {
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
                                                              this@KafkaConsumerOutput,
                                                              KafkaMessagesBundle.message("consumer.table.awaiting"))
    }
  }

  fun setMaxRows(limit: Int) {
    outputModel.maxElementsCount = limit
  }

  fun addRow(element: ConsumerOutputRow) {
    outputModel.addElement(element)
    if (outputTableStatusDelegate.isInitialized()) {
      outputTableStatus.addRecord(element.record.getOrThrow())
    }
  }

  fun addError(element: ConsumerOutputRow) {
    outputModel.addElement(element)
  }

  fun getElements(): List<ConsumerOutputRow> {
    return outputModel.elements().toList()
  }

  private fun setupTablePopupMenu(table: JTable) {
    val clearAction = SimpleDumbAwareAction(KafkaMessagesBundle.message("action.clear.output")) { outputModel.clear() }
    PopupHandler.installPopupMenu(table, DefaultActionGroup().apply {
      (ActionManager.getInstance().getAction("BdIde.TableEditor.PopupActionGroup") as? ActionGroup)?.let { addAll(it) }
      addSeparator()
      addAction(clearAction)
    }, "KafkaConsumerPanel")
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
}