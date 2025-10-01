package io.confluent.intellijplugin.core.table.filters

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import kotlin.math.max

class TableFilterHeader(table: JTable) : JPanel(BorderLayout()), PropertyChangeListener {

  var columnsController: FilterColumnsControllerPanel? = null

  private var caseInsensitive = false
    set(value) {
      field = value
      if (rowFilterDelegate.isInitialized()) {
        rowFilter.compareCaseInsensitive = value
      }
    }

  private val positionHelper = PositionHelper(this)

  private val rowFilterDelegate = lazy { TableRowFilter(table).apply { compareCaseInsensitive = caseInsensitive } }
  private val rowFilter: TableRowFilter by rowFilterDelegate

  var table: JTable? = table
    set(table) {
      val oldTable = field
      positionHelper.changeTable(oldTable, table)
      if (oldTable != null) {
        oldTable.removeComponentListener(resizer)
        oldTable.removePropertyChangeListener("model", this)
        oldTable.removePropertyChangeListener("componentOrientation", this)
        (oldTable.rowSorter as? TableRowSorter)?.rowFilter = null
      }
      field = table
      if (table == null) {
        removeController()
        revalidate()
      }
      else {
        if (rowFilterDelegate.isInitialized()) {
          rowFilter.table = table
          rowFilter.setConditions(emptyList())
        }
        recreateController()
        table.addComponentListener(resizer)
        table.addPropertyChangeListener("model", this)
        table.addPropertyChangeListener("componentOrientation", this)
      }
    }

  var position: Position?
    get() = positionHelper.position
    set(location) {
      positionHelper.position = location
    }

  init {
    isOpaque = false
    position = Position.INLINE
    this.table = table
  }

  private val resizer: ComponentAdapter = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      columnsController?.revalidate()
    }
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if ("model" == evt.propertyName || "componentOrientation" == evt.propertyName) {
      recreateController()
    }
  }

  override fun updateUI() {
    super.updateUI()
    recreateController()
  }

  override fun setVisible(flag: Boolean) {
    if (isVisible != flag) {
      positionHelper.headerVisibilityChanged(flag)
    }
    super.setVisible(flag)
    positionHelper.headerVisibilityChanged(flag)
  }

  override fun addNotify() {
    super.addNotify()
    positionHelper.filterHeaderContainmentUpdate()
  }

  private fun removeController(): Boolean {
    if (columnsController != null) {
      columnsController!!.detach()
      remove(columnsController)
      columnsController = null
      return true
    }
    return false
  }

  private fun recreateController() {
    removeController()
    val table = table ?: return
    val columnsController = FilterColumnsControllerPanel(table, font, foreground)
    this.columnsController = columnsController
    add(columnsController, BorderLayout.WEST)
    revalidate()
  }

  enum class Position {
    TOP, INLINE, NONE, REPLACE
  }

  inner class FilterColumnsControllerPanel(private val table: JTable, font: Font?, foreground: Color?) :
    JPanel(null), TableColumnModelListener, Runnable, Iterable<FilterEditor?> {

    private val columns: LinkedList<FilterColumnPanel>
    private val preferredSize: Dimension
    private val tableColumnModel: TableColumnModel
    private var autoRun = 0
    private var handlerEnabled: Boolean? = null
    private val tableModel: TableModel

    init {
      super.setFont(font)
      super.setForeground(foreground)
      isOpaque = false
      tableColumnModel = table.columnModel
      tableModel = table.model

      val count = tableColumnModel.columnCount
      columns = LinkedList()
      for (i in 0 until count) {
        createColumn(i)
      }
      preferredSize = Dimension(0, if (count == 0) 0 else columns[0].h)
      placeComponents()
      tableColumnModel.addColumnModelListener(this)
    }

    override fun iterator(): MutableIterator<FilterEditor> {
      val it: Iterator<FilterColumnPanel> = columns.iterator()
      return object : MutableIterator<FilterEditor> {
        override fun remove() {}
        override fun next(): FilterEditor {
          return it.next().editor
        }

        override fun hasNext(): Boolean {
          return it.hasNext()
        }
      }
    }

    private fun updateColumnBorder(editor: FilterEditor) {
      val viewIndex = table.convertColumnIndexToView(editor.modelIndex)
      editor.border = if (viewIndex == 0) IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      else IdeBorderFactory.createBorder(SideBorder.LEFT or SideBorder.BOTTOM)
    }

    private fun createColumn(columnView: Int) {
      val columnModel = table.convertColumnIndexToModel(columnView)
      val editor = FilterEditor(columnModel)
      updateColumnBorder(editor)
      editor.addListener {
        val rowFiler = rowFilter
        rowFiler.setConditions(columns.mapNotNull {
          val text = it.editor.text
          if (text.isNullOrBlank()) null else it.tableColumn.modelIndex to text
        })
        (table.rowSorter as TableRowSorter).rowFilter = rowFiler
        //ToDo Hack. To proper repaint "Nothing to show" empty state which is drawn on scrollpane view.
        table.parent?.repaint()
      }
      val column = FilterColumnPanel(tableColumnModel.getColumn(columnView), editor)
      column.updateHeight()
      columns.add(column)
      add(column)
    }

    fun detach() {
      for (column in columns) {
        column.detach()
      }
      tableColumnModel.removeColumnModelListener(this)
    }

    private fun updateHeight() {
      var h = 0
      for (c in columns) {
        h = max(h, c.h)
      }
      preferredSize.height = h
      placeComponents()
      repaint()
    }

    override fun columnMarginChanged(e: ChangeEvent) {
      placeComponents()
    }

    override fun columnMoved(e: TableColumnModelEvent) {
      if (e.fromIndex != e.toIndex) {

        if(e.fromIndex == 0 ||  e.toIndex == 0) {
          updateColumnBorder(columns[e.fromIndex].editor)
          updateColumnBorder(columns[e.toIndex].editor)
        }

        val fcp = columns.removeAt(e.fromIndex)
        columns.add(e.toIndex, fcp)
        placeComponents()
      }
      val header = table.tableHeader
      val tc = header.draggedColumn
      if (tc != null) {
        val rightToLeft = table.componentOrientation == ComponentOrientation.RIGHT_TO_LEFT
        val it = if (rightToLeft) columns.descendingIterator() else columns.iterator()
        var previous: FilterColumnPanel? = null
        while (it.hasNext()) {
          val fcp = it.next()
          if (fcp.tableColumn === tc) {
            var r: Rectangle? = null
            var x = 0.0
            if (previous != null) {
              r = previous.bounds
              x = r.getX() + r.getWidth()
            }
            r = fcp.getBounds(r)
            r.translate((x - r.getX() + header.draggedDistance).toInt(), 0)
            fcp.bounds = r
            if (rightToLeft) {
              previous = if (it.hasNext()) it.next() else null
            }
            if (previous != null) {
              val prevZOrder = getComponentZOrder(previous)
              val zOrder = getComponentZOrder(fcp)
              val overPreviousDragging = if (rightToLeft) header.draggedDistance > 0 else header.draggedDistance < 0
              if (overPreviousDragging != zOrder < prevZOrder) {
                setComponentZOrder(previous, zOrder)
                setComponentZOrder(fcp, prevZOrder)
              }
            }
            break
          }
          previous = fcp
        }
      }
    }

    override fun columnAdded(e: TableColumnModelEvent) {
      if (isCorrectModel) {
        createColumn(e.toIndex)
        update()
      }
    }

    override fun columnRemoved(e: TableColumnModelEvent) {
      if (isCorrectModel) {
        val fcp = columns.removeAt(e.fromIndex)
        fcp.detach()
        remove(fcp)
        update()
      }
    }

    override fun columnSelectionChanged(e: ListSelectionEvent) {}
    private val isCorrectModel: Boolean
      get() = tableModel === table.model

    private fun update() {
      autoRun += 1
      if (SwingUtilities.isEventDispatchThread()) {
        SwingUtilities.invokeLater(this)
      }
      else {
        run()
      }
    }

    override fun run() {
      if (--autoRun == 0) {
        handlerEnabled = null
        updateHeight()
      }
    }

    fun placeComponents() {
      var x = 0
      val it = if (table.componentOrientation == ComponentOrientation.RIGHT_TO_LEFT) columns.descendingIterator() else columns.iterator()

      while (it.hasNext()) {
        val fcp = it.next()
        fcp.setBounds(x, 0, fcp.w, preferredSize.height)
        x += fcp.w
      }
      revalidate()
    }

    override fun getPreferredSize(): Dimension {
      preferredSize.width = table.width
      return preferredSize
    }

    private inner class FilterColumnPanel(var tableColumn: TableColumn, var editor: FilterEditor) : JPanel(
      BorderLayout()), PropertyChangeListener {

      var w: Int
      var h: Int

      init {
        isOpaque = false
        w = tableColumn.width
        add(editor, BorderLayout.CENTER)
        h = preferredSize.height
        tableColumn.addPropertyChangeListener(this)
      }

      fun detach() {
        remove(editor)
        tableColumn.removePropertyChangeListener(this)
      }

      fun updateHeight() {
        h = preferredSize.height
        revalidate()
      }

      override fun propertyChange(evt: PropertyChangeEvent) {
        val newW = tableColumn.width
        if (w != newW) {
          w = newW
          placeComponents()
        }
      }
    }
  }
}