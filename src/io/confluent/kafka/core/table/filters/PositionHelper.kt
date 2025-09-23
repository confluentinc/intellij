package io.confluent.kafka.core.table.filters

import java.awt.BorderLayout
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JViewport
import javax.swing.table.JTableHeader

internal class PositionHelper(var filterHeader: TableFilterHeader) : PropertyChangeListener {

  var location: TableFilterHeader.Position? = null

  private var headerViewport: JViewport? = null

  private var previousTableViewport: Component? = null

  var position: TableFilterHeader.Position?
    get() = location
    set(location) {
      this.location = location
      val table = filterHeader.table
      changeTable(table, table)
    }

  val tableHeader: Component?
    get() =filterHeader.components.firstOrNull { it is  JTableHeader}

  fun headerVisibilityChanged(visible: Boolean) {
    val table = filterHeader.table
    changeTable(table, null)
    if (visible) {
      changeTable(null, table)
    }
  }

  fun changeTable(oldTable: JTable?, newTable: JTable?) {
    oldTable?.removePropertyChangeListener("ancestor", this)
    cleanUp()
    if (newTable != null) {
      newTable.addPropertyChangeListener("ancestor", this)
      trySetUp(newTable)
    }
  }

  fun filterHeaderContainmentUpdate() {
    if (!canHeaderLocationBeManaged()) {
      cleanUp()
    }
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (previousTableViewport !== evt.newValue || evt.source !== filterHeader.table) {
      previousTableViewport = null
      cleanUp()
      trySetUp(filterHeader.table)
    }
  }

  private fun canHeaderLocationBeManaged(): Boolean {
    if (location === TableFilterHeader.Position.NONE) {
      return false
    }
    val parent = filterHeader.parent
    return parent == null || parent === headerViewport
  }

  private fun trySetUp(table: JTable?) {
    if (table != null && table.isVisible && canHeaderLocationBeManaged()
        && filterHeader.isVisible) {
      val tableParent = table.parent
      if (tableParent is JViewport) {
        val gp = tableParent.getParent()
        if (gp is JScrollPane) {
          val viewport = gp.viewport
          if (viewport != null && viewport.view === table) {
            setUp(gp)
            previousTableViewport = tableParent
          }
        }
      }
    }
  }

  private inner class PositionHelperViewport : JViewport() {
    override fun setView(view: Component?) {
      if (view is JTableHeader) {
        removeTableHeader()
        view.setVisible(position != TableFilterHeader.Position.REPLACE)
        filterHeader.add(view, if (position == TableFilterHeader.Position.INLINE) BorderLayout.NORTH else BorderLayout.SOUTH)
        filterHeader.revalidate()
        super.setView(filterHeader)
      }
    }

    private fun removeTableHeader() {
      val tableHeader = tableHeader
      if (tableHeader != null) {
        filterHeader.remove(tableHeader)
      }
    }

    init {
      isOpaque = false
    }
  }

  private fun setUp(scrollPane: JScrollPane) {
    headerViewport = PositionHelperViewport()
    val currentColumnHeader = scrollPane.columnHeader
    if (currentColumnHeader != null) {
      val view = currentColumnHeader.view
      if (view != null) {
        headerViewport!!.view = view
      }
    }
    scrollPane.columnHeader = headerViewport
  }

  private fun cleanUp() {
    val currentViewport = headerViewport
    headerViewport = null
    if (currentViewport != null) {
      currentViewport.remove(filterHeader)
      val parent = currentViewport.parent
      if (parent is JScrollPane) {
        if (parent.columnHeader === currentViewport) {
          val tableHeader = tableHeader
          val newView = if (tableHeader == null) null else createCleanViewport(tableHeader)
          parent.columnHeader = newView
        }
      }
    }
  }

  companion object {
    private fun createCleanViewport(tableHeader: Component): JViewport {
      val ret = JViewport()
      ret.view = tableHeader
      return ret
    }
  }
}