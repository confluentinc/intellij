package io.confluent.kafka.core.rfs.editorviewer

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.Cell
import com.intellij.ui.PopupHandler
import com.intellij.ui.SearchTextField
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.PairFunction
import io.confluent.kafka.core.rfs.copypaste.RfsCopyPasteSupport
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.DummyFileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.projectview.actions.RfsActionPlaces
import io.confluent.kafka.core.rfs.projectview.actions.RfsPaneOwner
import io.confluent.kafka.core.rfs.search.impl.BackListElement
import io.confluent.kafka.core.rfs.search.impl.BdtConnectionSearcher
import io.confluent.kafka.core.rfs.search.impl.ListElement
import io.confluent.kafka.core.rfs.search.impl.MoreListElement
import io.confluent.kafka.core.table.extension.TableResizeController
import io.confluent.kafka.core.table.getColumnByName
import io.confluent.kafka.core.table.removeColumn
import io.confluent.kafka.core.table.renderers.CustomTableCellRenderer
import io.confluent.kafka.core.ui.StatusTextAnimator
import io.confluent.kafka.core.ui.doOnChange
import io.confluent.kafka.core.util.SizeUtils
import io.confluent.kafka.core.util.invokeLater
import io.confluent.kafka.core.util.toPresentableText
import io.confluent.kafka.util.KafkaMessagesBundle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import kotlin.math.min

fun interface PathChangeListenerListener : EventListener {
  fun pathChanged(rfsPath: RfsPath)
}

class RfsTableViewer(val project: Project, val driver: Driver, rfsPath: RfsPath, val searchField: SearchTextField) : Disposable {
  private val isSearchIgnoreChanges = AtomicBoolean(false)
  val dataTableModel = RfsTableModel(driver, driver.getMetaInfoProvider().getAllTableColumns())

  val owner = RfsTablePaneOwner(this).also {
    Disposer.register(this, it)
  }

  private val copyPasteSupport = RfsCopyPasteSupport(owner)

  val table = JBTable(dataTableModel).apply {
    rowSorter = RfsTableRowSorter(dataTableModel).apply {
      // Sort by name (0 column always) by default.
      sortKeys = listOf(RowSorter.SortKey(0, SortOrder.ASCENDING))
    }
  }

  private val loadingAnimator = StatusTextAnimator(table.emptyText, listOf(KafkaMessagesBundle.message("table.loading") + ".  ",
                                                                           KafkaMessagesBundle.message("table.loading") + ".. ",
                                                                           KafkaMessagesBundle.message("table.loading") + "..."))

  private var currentPath = rfsPath
    set(value) {

      if (field == value) {
        return
      }

      field = value

      currentPathChangeListeners.forEach { it.pathChanged(value) }
    }

  private val currentPathChangeListeners = mutableListOf<PathChangeListenerListener>()

  init {
    // Removing invisible columns
    driver.getMetaInfoProvider().getAllTableColumns().filter {
      !RfsFileViewerSettings.getInstance().isColumnVisible(driver, it.id)
    }.forEach {
      table.removeColumn(it.name)
    }

    updateTableColumnRenderers()

    TableSpeedSearch.installOn(table, PairFunction { obj: Any, cell: Cell ->
      val listElement = obj as? ListElement ?: return@PairFunction obj.toString()
      if (listElement is MoreListElement || listElement is BackListElement) return@PairFunction ""
      if (cell.column == 0)
        listElement.fileInfo.name
      else {
        val fileInfo = listElement.fileInfo
        dataTableModel.columns[cell.column - 1].valueForFileInfo(fileInfo).toString()
      }
    })

    PopupHandler.installPopupMenu(table, "BigDataTools.RfsPane.PopupActionGroup", RfsActionPlaces.RFS_TABLE_VIEW_POPUP)

    table.showHorizontalLines = false
    table.showVerticalLines = false

    table.columnModel.addColumnModelListener(object : TableColumnModelListener {
      override fun columnAdded(e: TableColumnModelEvent) = updateTableColumnRenderers()
      override fun columnRemoved(e: TableColumnModelEvent?) = Unit
      override fun columnMoved(e: TableColumnModelEvent?) = Unit
      override fun columnMarginChanged(e: ChangeEvent?) = Unit
      override fun columnSelectionChanged(e: ListSelectionEvent?) = Unit
    })

    TableResizeController.installOn(table).apply {
      setResizePriorityList("Name")
      onlyExpand = true
      mode = TableResizeController.Mode.PRIOR_COLUMNS_LIST
    }

    table.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return

        val selectedNode = getSelectedNode() ?: return
        if (e.clickCount == 2) {
          openFileOrFolder(selectedNode)
        }
        else if (e.clickCount == 1 && getSelectedNode() is MoreListElement) {
          openFileOrFolder(selectedNode)
        }
      }
    })

    table.addKeyListener(RfsEditorKeyAdapter())

    searchField.textEditor.doOnChange {
      if (isSearchIgnoreChanges.get())
        return@doOnChange

      rebuildList(currentPath) {
        searchField.grabFocus()
      }
    }

    searchField.textEditor.addKeyListener(RfsSearchKeyAdapter())

    DumbAwareAction.create {
      searchField.grabFocus()
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ctrl F"), table, this)

    rebuildList(rfsPath)
  }

  fun addListener(listener: PathChangeListenerListener) {
    currentPathChangeListeners += listener
  }

  fun removeListener(listener: PathChangeListenerListener) {
    currentPathChangeListeners -= listener
  }

  private fun clearSearch() {
    isSearchIgnoreChanges.set(true)
    searchField.text = ""
    isSearchIgnoreChanges.set(false)
  }

  private fun updateTableColumnRenderers() {
    table.columnModel.columns.asIterator().forEach { tableColumn ->
      val index = dataTableModel.columns.indexOf { tableColumn.headerValue == it.name }
      if (index == -1) {
        return@forEach
      }
      tableColumn.cellRenderer = CustomTableCellRenderer<ListElement> { element ->
        val fileInfo = element.fileInfo
        if (fileInfo.isDirectory || fileInfo is DummyFileInfo) return@CustomTableCellRenderer null
        dataTableModel.columns[index].valueForFileInfo(fileInfo)
      }
    }

    table.getColumnByName("Name")?.cellRenderer = RfsTableViewerCellRenderer()
    table.getColumnByName("Size")?.cellRenderer = CustomTableCellRenderer<ListElement> { element ->
      val fileInfo = element.fileInfo
      if (fileInfo.length <= 0) return@CustomTableCellRenderer null
      SizeUtils.toString(fileInfo.length)
    }
  }

  private fun focusOn(path: RfsPath) {
    if (searchField.isFocusOwner)
      return
    var modelRowIndex = -1
    for (i in 0 until dataTableModel.rowCount) {
      if (dataTableModel.getEntry(i)?.rfsPath == path) {
        modelRowIndex = i
        break
      }
    }

    if (modelRowIndex != -1) {
      val viewRowIndex = table.convertRowIndexToView(modelRowIndex)

      tableChangeSelector(viewRowIndex)
      table.scrollRectToVisible(table.getCellRect(viewRowIndex, 0, true))
    }
  }

  private fun openFileOrFolder(element: ListElement) {
    if (element is MoreListElement) {
      dataTableModel.remove(element)
      rebuildList(currentPath, element.nextBatchId)
    }
    else if (element is BackListElement) {
      clearSearch()
      val parentPath = currentPath.parent
      if (parentPath != null) {
        rebuildList(parentPath, doIfSuccess = { focusOn(element.rfsPath) })
      }
    }
    else if (element.rfsPath.isFile) {
      BdtConnectionSearcher.goToFound(element.fileInfo.driver.getExternalId(), element.rfsPath, project)
    }
    else {
      clearSearch()
      rebuildList(element.rfsPath)
    }
  }

  fun getSelectedNode(): ListElement? {
    if (table.selectedRow == -1) {
      return null
    }
    val modelIndex = table.convertRowIndexToModel(table.selectedRow)
    return dataTableModel.getEntry(modelIndex)
  }

  private val component: JBScrollPane = object : JBScrollPane(table), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[PlatformDataKeys.CUT_PROVIDER] = copyPasteSupport.cutProvider
      sink[PlatformDataKeys.COPY_PROVIDER] = copyPasteSupport.copyProvider
      sink[PlatformDataKeys.PASTE_PROVIDER] = copyPasteSupport.pasteProvider
      sink[RfsPaneOwner.DATA_KEY] = owner
      sink[LangDataKeys.NO_NEW_ACTION] = true
    }
  }

  fun getComponent() = component

  override fun dispose() = Unit

  fun openPath(rfsPath: RfsPath) = rebuildList(rfsPath)

  private fun rebuildList(rfsPath: RfsPath, batchId: String? = null, doIfSuccess: (() -> Unit)? = null) {
    currentPath = rfsPath

    val searchText = searchField.text
    val withSearchPath = if (searchText.isBlank())
      rfsPath
    else
      rfsPath.addRelative(searchText.removeSuffix("/"), isDirectory = false)

    if (batchId == null) {
      loadingAnimator.start()
      invokeLater {
        dataTableModel.clear()
      }
    }

    val promise = BdtConnectionSearcher.list(driver, withSearchPath, batchId)
    promise.onProcessed {
      it ?: return@onProcessed
      invokeLater {
        val results = it.results
        results.forEach { searchElement ->
          searchElement.icon = driver.treeNodeBuilder.getNode(project, driver, searchElement.rfsPath, searchElement.fileInfo).getIdleIcon()
        }

        if (batchId == null) {
          val rfsPathParent = withSearchPath.parent
          if (rfsPathParent != null) {
            dataTableModel.setData(mutableListOf<ListElement>(
              BackListElement(DummyFileInfo(name = "..", path = withSearchPath, externalPath = "", driver))
            ).apply { addAll(results) })
            tableChangeSelector(0)
          }
          else {
            dataTableModel.setData(results.toMutableList())
            if (table.rowCount != 0 && table.columnCount != 0) {
              tableChangeSelector(0)
            }
          }
        }
        else {
          dataTableModel.addData(results)
        }

        if (dataTableModel.rowCount != 0) {
          if (it.nextBatchId != null) {
            dataTableModel.addData(listOf(MoreListElement(driver, it.nextBatchId)))
          }
        }
        else {
          table.emptyText.text = KafkaMessagesBundle.message("search.view.empty.text")
        }
        table.revalidate()
        doIfSuccess?.invoke()
      }
    }.onError { e ->
      invokeLater {
        dataTableModel.clear()
        loadingAnimator.stop()
        table.emptyText.text = e.toPresentableText()
      }
    }.then {
      invokeLater { loadingAnimator.stop() }
    }
  }

  private fun tableChangeSelector(index: Int) {
    val isSearchFocused = searchField.isFocusOwner
    table.changeSelection(index, 0, false, false)
    if (isSearchFocused)
      searchField.grabFocus()
  }

  inner class RfsEditorKeyAdapter : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      when (e.keyCode) {
        KeyEvent.VK_ENTER -> getSelectedNode()?.let { openFileOrFolder(it) }
        KeyEvent.VK_F -> if (e.isControlDown) {
          searchField.grabFocus()
        }
        KeyEvent.VK_BACK_SPACE -> {
          val capturedPath = currentPath
          clearSearch()
          currentPath.parent?.let { rebuildList(it, doIfSuccess = { focusOn(capturedPath) }) }
        }
        KeyEvent.VK_HOME -> {
          if (table.rowCount != 0 && table.columnCount != 0)
            tableChangeSelector(0)
        }
        KeyEvent.VK_END -> {
          if (table.rowCount != 0 && table.columnCount != 0)
            tableChangeSelector(table.rowCount - 1)
        }
      }
    }
  }

  inner class RfsSearchKeyAdapter : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {

      when (e.keyCode) {
        KeyEvent.VK_ENTER -> getSelectedNode()?.let { openFileOrFolder(it) }
        KeyEvent.VK_UP -> if (table.rowCount != 0 && table.columnCount != 0 && table.selectedRow > 0)
          tableChangeSelector(table.selectedRow - 1)
        KeyEvent.VK_DOWN -> if (table.rowCount != 0 && table.columnCount != 0 && table.selectedRow < table.rowCount - 1)
          tableChangeSelector(table.selectedRow + 1)
        KeyEvent.VK_HOME -> {
          if (table.rowCount != 0 && table.columnCount != 0)
            tableChangeSelector(0)
        }
        KeyEvent.VK_END -> {
          if (table.rowCount != 0 && table.columnCount != 0)
            tableChangeSelector(table.rowCount - 1)
        }
      }
    }
  }
}

private fun <E> List<E>.indexOf(since: Int = 0, till: Int = this.size, notFound: Int = -1, predicate: (E) -> Boolean): Int {
  if (since < 0) throw IllegalArgumentException("Called List.indexOf() with negative since: $since")
  if (this.isEmpty()) return notFound
  val n = this.size
  if (till <= since || since >= n) return notFound
  for (i in since until min(till, n)) if (predicate(this[i])) return i
  return notFound
}