package io.confluent.intellijplugin.core.rfs.projectview.dnd

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSource
import com.intellij.util.ui.ImageUtil
import io.confluent.intellijplugin.core.rfs.projectview.actions.RfsPaneOwner
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Point
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSourceDropEvent
import java.awt.image.BufferedImage
import javax.swing.JLabel

class RfsPaneDndSource(val pane: RfsPaneOwner) : DnDSource {
  override fun dropActionChanged(gestureModifiers: Int) {}

  override fun startDragging(action: DnDAction, dragOrigin: Point) = DnDDragStartBean(pane.getSelectionPaths())

  override fun createDraggedImage(action: DnDAction?,
                                  dragOrigin: Point?,
                                  bean: DnDDragStartBean): com.intellij.openapi.util.Pair<Image, Point> {
    val paths = pane.getSelectionPaths()

    val count = paths.size

    val label = JLabel(KafkaMessagesBundle.message("rfs.pane.dnd.image.label", count, if (count == 1) 0 else 1))
    label.isOpaque = true
    label.foreground = pane.jTree.foreground
    label.background = pane.jTree.background
    label.font = pane.jTree.font
    label.size = label.preferredSize
    val image = ImageUtil.createImage(label.width, label.height, BufferedImage.TYPE_INT_ARGB)

    val g2 = image.graphics as Graphics2D
    g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)
    label.paint(g2)
    g2.dispose()

    return com.intellij.openapi.util.Pair(image, Point(-image.getWidth(null), -image.getHeight(null)))
  }

  override fun canStartDragging(action: DnDAction, dragOrigin: Point): Boolean {
    if (action.actionId and DnDConstants.ACTION_COPY_OR_MOVE == 0)
      return false

    val driverNodes = pane.getSelectedDriverNodes()
    if (driverNodes.size != pane.getSelectionPaths().size)
      return false

    return driverNodes.all {
      it.fileInfo != null && !it.isMount && it.fileInfo?.isCopySupport == true
    }
  }

  override fun dragDropEnd(dragEvent: DnDEvent?, dropEvent: DragSourceDropEvent?) {}
}