package com.jetbrains.bigdatatools.kafka.core.rfs.projectview.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.awt.RelativeRectangle
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.RfsCopyPasteManager
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.LocalDriverManager
import com.jetbrains.bigdatatools.kafka.core.rfs.statistics.RfsConnectionUsageCollector
import java.awt.Rectangle
import java.io.File
import javax.swing.tree.TreePath

/**
 * The special fix to support DND from RFS Pane to Project pane.
 *
 * We need to hack dnd target for project pane by wrapping it
 */
object RfsProjectPaneDndSupport {
  fun setup(project: Project, disposable: Disposable) {
    try {
      val projectViewPane = ProjectView.getInstance(project).getProjectViewPaneById(ProjectViewPane.ID)
      val projectPaneTree = projectViewPane?.tree ?: return

      val dndTarget = projectPaneTree.getClientProperty("DnD Target") as? DnDTarget ?: return
      val wrappedDndTarget = object : DnDTarget by dndTarget {
        override fun update(event: DnDEvent): Boolean {
          if (wrappedUpdate(event)) {
            return true
          }

          return dndTarget.update(event)
        }

        override fun drop(event: DnDEvent) {
          if (!wrappedDrop(event))
            return dndTarget.drop(event)
        }

        private fun wrappedUpdate(event: DnDEvent): Boolean {
          event.isDropPossible = false

          RfsDndUtils.getSourceFileInfosFromRfsDnd(event) ?: return false

          val point = event.point ?: return false

          val targetTreePath: TreePath = projectPaneTree.getClosestPathForLocation(point.x, point.y)

          val bounds: Rectangle = projectPaneTree.getPathBounds(targetTreePath) ?: return false
          if (bounds.y > point.y || point.y >= bounds.y + bounds.height)
            return false

          targetTreePath.targetFile ?: return false

          event.isDropPossible = true
          event.setHighlighting(RelativeRectangle(projectPaneTree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE)

          return true
        }

        private fun wrappedDrop(event: DnDEvent): Boolean {
          event.setDropPossible(false, "")

          val sourceFileInfos = RfsDndUtils.getSourceFileInfosFromRfsDnd(event) ?: return false
          val targetFile = getTargetFile(event) ?: return false
          val targetFileInfo = LocalDriverManager.instance.createFileInfo(targetFile)

          RfsCopyPasteManager.copyMoveWithDialog(project = project,
                                                 targetPath = targetFileInfo.path,
                                                 targetDriver = targetFileInfo.driver,
                                                 sourceFiles = sourceFileInfos,
                                                 allowMove = false,
                                                 onResult = {
                                                   RfsConnectionUsageCollector.collectDND(targetFileInfo.driver, sourceFileInfos)
                                                 })
          return true
        }

        private val TreePath.targetFile: File?
          get() {
            val targetElement = projectViewPane.getElementsFromNode(lastPathComponent).firstOrNull()
            return getVirtualFile(targetElement)?.toNioPath()?.toFile()
          }

        private fun getTargetFile(event: DnDEvent): File? {
          val point = event.point ?: return null

          val treePath: TreePath = projectPaneTree.getClosestPathForLocation(point.x, point.y)
          return treePath.targetFile
        }
      }

      DnDManager.getInstance().registerTarget(wrappedDndTarget, projectPaneTree)

      Disposer.register(disposable, Disposable {
        DnDManager.getInstance().unregisterTarget(wrappedDndTarget, projectPaneTree)
        DnDManager.getInstance().registerTarget(dndTarget, projectPaneTree)
      })
    }
    catch (t: Throwable) {
      logger<RfsProjectPaneDndSupport>().error("Cannot setup wrapper for Project Pane", t)
    }
  }


  //Is not used PsiUtils.getVirtualFile because of the BDIDE-3219
  fun getVirtualFile(element: PsiElement?): VirtualFile? {
    // optimisation: call isValid() on file only to reduce walks up and down
    if (element == null) {
      return null
    }
    if (element is PsiFileSystemItem) {
      return if (element.isValid()) element.virtualFile else null
    }
    val containingFile = element.containingFile
    if (containingFile == null || !containingFile.isValid) {
      return null
    }
    var file = containingFile.virtualFile
    if (file == null) {
      val originalFile = containingFile.originalFile
      if (originalFile !== containingFile && originalFile.isValid) {
        file = originalFile.virtualFile
      }
    }
    return file
  }
}