package io.confluent.intellijplugin.core.rfs.projectview.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.local.LocalDriverManager
import io.confluent.intellijplugin.core.rfs.projectview.actions.RfsActionUtil
import io.confluent.intellijplugin.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import java.io.File
import javax.swing.tree.TreePath

object RfsDndUtils {
  fun getSourceFileInfos(event: DnDEvent, project: Project): List<FileInfo>? =
    getSourceFileInfosFromRfsDnd(event) ?: getSourceFileInfosFromProjectDnd(project, event)

  fun getSourceFileInfosFromRfsDnd(event: DnDEvent): List<FileInfo>? {
    val attachedObject = event.attachedObject as? Array<*>
    val treePaths = attachedObject?.filterIsInstance<TreePath>() ?: return null
    val filtered = RfsActionUtil.excludeNestedPaths(treePaths)
    return filtered.mapNotNull { getFileInfoFromRfsTreePath(it) }.ifEmpty { null }
  }

  private fun getSourceFileInfosFromProjectDnd(project: Project, event: DnDEvent): List<FileInfo>? {
    val projectViewPane = ProjectView.getInstance(project).getProjectViewPaneById(ProjectViewPane.ID) ?: return null

    val wrapper = event.attachedObject as? TransferableWrapper
    val treePaths = wrapper?.treePaths
    return treePaths?.filterIsInstance<TreePath>()?.mapNotNull {
      getFileInfoFromProjectTreePath(projectViewPane, it)
    }?.ifEmpty { null }
  }

  private fun getFileInfoFromRfsTreePath(treePath: TreePath) = treePath.lastDriverNode?.fileInfo

  private fun getFileInfoFromProjectTreePath(projectViewPane: AbstractProjectViewPane, treePath: TreePath): FileInfo? {
    val targetElement = projectViewPane.getElementsFromNode(treePath.lastPathComponent).firstOrNull() ?: return null
    val virtualFile = targetElement.containingFile.virtualFile ?: return null

    val canonicalPath = PathUtil.getLocalPath(virtualFile) ?: return null
    val file = File(canonicalPath)
    return LocalDriverManager.instance.createFileInfo(file)
  }
}