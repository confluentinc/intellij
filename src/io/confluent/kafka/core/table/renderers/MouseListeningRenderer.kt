package io.confluent.kafka.core.table.renderers

import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener

interface MouseListeningRenderer : MouseListener, MouseMotionListener {

  //region MouseListener
  override fun mouseReleased(e: MouseEvent?) {}
  override fun mouseEntered(e: MouseEvent?) {}
  override fun mouseClicked(e: MouseEvent?) {}
  override fun mouseExited(e: MouseEvent?) {}
  override fun mousePressed(e: MouseEvent?) {}
  //endregion MouseListener

  //region MouseMotionListener
  override fun mouseMoved(e: MouseEvent?) {}
  override fun mouseDragged(e: MouseEvent?) {}
  //endregion MouseMotionListener
}