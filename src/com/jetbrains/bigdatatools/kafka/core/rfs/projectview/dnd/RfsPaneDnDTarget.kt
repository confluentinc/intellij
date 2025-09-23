package io.confluent.kafka.core.rfs.projectview.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDTarget
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.awt.RelativeRectangle
import io.confluent.kafka.core.rfs.copypaste.RfsCopyPasteManager
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.copyhandler.InterDriverCopyManager
import io.confluent.kafka.core.rfs.projectview.actions.RfsPaneOwner
import io.confluent.kafka.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.Point
import javax.swing.tree.TreePath

class RfsPaneDnDTarget(val pane: RfsPaneOwner, private val defaultDriver: Driver?, private val defaultRfsPath: RfsPath?) : DnDTarget {
  override fun cleanUpOnLeave() {}

  override fun update(event: DnDEvent): Boolean {
    event.isDropPossible = false

    val point = event.point ?: return false

    val (targetDriver, targetPath) = getTargetDriverAndPath(point) ?: return false

    val closestPath = getPathForPoint(point)

    if (!targetPath.isDirectory)
      return false
    if (!targetDriver.fileInfoManager.getConnectionStatus().isConnected())
      return false

    val sourceFileInfos = RfsDndUtils.getSourceFileInfos(event, pane.project) ?: return false
    if (!InterDriverCopyManager.canCopyToFolder(sourceFileInfos, targetPath, targetDriver, false))
      return false

    val bounds = pane.jTree.getPathBounds(closestPath)
    if (bounds != null)
      event.setHighlighting(RelativeRectangle(pane.jTree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE)
    else
      event.setHighlighting(RelativeRectangle(pane.jTree, pane.jTree.bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE)

    event.setDropPossible(true, "Move")
    return true
  }

  override fun drop(event: DnDEvent?) {
    val point = event?.point ?: return

    val sourceFileInfos = RfsDndUtils.getSourceFileInfos(event, pane.project) ?: return

    val (targetDriver, targetPath) = getTargetDriverAndPath(point) ?: return
    RfsCopyPasteManager.copyMoveWithDialog(project = pane.project,
                                           targetPath = targetPath,
                                           targetDriver = targetDriver,
                                           sourceFiles = sourceFileInfos,
                                           onResult = {
                                             withContext(Dispatchers.IO) {
                                               LocalFileSystem.getInstance().refresh(false)
                                             }
                                           })
  }

  private fun getTargetDriverAndPath(point: Point): Pair<Driver, RfsPath>? {
    val destPath = getPathForPoint(point)

    return if (destPath != null) {
      getTargetForTreePath(destPath)
    }
    else {
      val driver = defaultDriver ?: return null
      val path = defaultRfsPath ?: return null
      driver to path
    }
  }

  private fun getTargetForTreePath(destPath: TreePath): Pair<Driver, RfsPath>? {
    val dest = destPath.lastDriverNode ?: return null
    val targetFileInfo = dest.fileInfo ?: return null
    val targetPath = targetFileInfo.path
    val targetDriver = targetFileInfo.driver
    return targetDriver to targetPath
  }

  override fun updateDraggedImage(image: Image?, dropPoint: Point?, imageOffset: Point?) {}

  private fun getPathForPoint(point: Point): TreePath? = pane.jTree.getPathForLocation(point.x, point.y)
}