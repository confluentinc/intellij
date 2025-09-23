package io.confluent.kafka.core.table

import com.intellij.ui.components.JBScrollPane
import java.awt.MouseInfo
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.plaf.ScrollPaneUI

/**
 * Special scroll pane for MaterialTable which handles mouse movements and clicks on viewport and selects appropriate rows in table.
 * Used for hover-row-under-mouse handling.
 *
 * IMPORTANT! Original JBScrollPane redispatches any mouse wheel events to its parent if it cannot be scrolled anymore.
 *            This class redispatch only vertical wheel events and events which scrolls to the right!
 */
class MaterialJBScrollPane(val table: MaterialTable) : JBScrollPane(table), MouseListener, MouseMotionListener, ChangeListener {

  init {
    viewport.addMouseListener(this)
    viewport.addMouseMotionListener(this)
    viewport.addChangeListener(this)
  }

  override fun setUI(ui: ScrollPaneUI) {
    super.setUI(ui)
    JBScrollPaneUtils.disableHorizontalWheelRedispatch(this)
  }

  //region MouseListener
  override fun mouseReleased(e: MouseEvent?) {}

  override fun mouseEntered(e: MouseEvent?) {}

  override fun mouseClicked(e: MouseEvent) {
    val point = SwingUtilities.convertPoint(this, e.point.x, e.point.y, table)
    val row = table.rowAtPoint(point) + 1
    if (row >= 0 && row < table.rowCount) {
      table.setRowSelectionInterval(row, row)
    }
    else {
      table.clearSelection()
    }
  }

  override fun mouseExited(e: MouseEvent?) {
    table.rollOverRowIndex = -1
    repaint()
  }

  override fun mousePressed(e: MouseEvent?) {}
  //endregion MouseListener

  //region MouseMotionListener
  override fun mouseMoved(e: MouseEvent) {
    val point = SwingUtilities.convertPoint(this, e.point.x, e.point.y, table)
    updateHoverRow(table.rowAtPoint(point) + 1)
  }

  override fun mouseDragged(e: MouseEvent?) {}
  //endregion MouseMotionListener

  //region ChangeListener
  override fun stateChanged(e: ChangeEvent?) {
    val point = MouseInfo.getPointerInfo()?.location ?: return
    SwingUtilities.convertPointFromScreen(point, table)
    updateHoverRow(table.rowAtPoint(point))
  }
  //endregion ChangeListener

  protected fun updateHoverRow(row: Int) {
    if (row != table.rollOverRowIndex) {
      table.rollOverRowIndex = row
      repaint()
    }
  }
}