package io.confluent.kafka.core.rfs.driver.local.node

import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.ui.DeferredIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.RowIcon
import com.intellij.util.ui.EmptyIcon
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.local.LocalFileInfo
import io.confluent.kafka.core.rfs.driver.writeLocked
import io.confluent.kafka.core.rfs.icons.RfsIcons
import io.confluent.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import javax.swing.Icon

class LocalDriverRfsNode(private val localProject: Project,
                         path: RfsPath,
                         driver: Driver
) : DriverFileRfsTreeNode(localProject, path, driver) {
  override fun getIdleIcon(): Icon? {
    val isWriteLocked = fileInfo?.writeLocked() ?: false

    val bdtSpecifiedIcon = super.getIdleIcon()
    if (bdtSpecifiedIcon == RfsIcons.REMOTE_DIRECTORY_LOCKED_ICON || bdtSpecifiedIcon == RfsIcons.REMOTE_DIRECTORY_ICON || bdtSpecifiedIcon == RfsIcons.FILE_ICON) {
      val file = (fileInfo as? LocalFileInfo)?.file
      val virtualFile = file?.toPath()?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
      if (virtualFile != null) {
        val fromVFile = try {
          getIconFromVFile(virtualFile)
        }
        catch (t: Throwable) {
          null
        }
        if (fromVFile != null) return fromVFile
      }
    }

    return when {
      rfsPath.isDirectory && isWriteLocked -> RfsIcons.DIRECTORY_LOCKED_ICON
      rfsPath.isDirectory -> RfsIcons.DIRECTORY_ICON
      else -> bdtSpecifiedIcon
    }
  }

  private fun getIconFromVFile(virtualFile: VirtualFile): Icon? {
    var icon: Icon? = null
    var isPlaceholder = false
    ApplicationManager.getApplication().runReadAction {
      val psiManager = PsiManager.getInstance(localProject)
      val psi = if (virtualFile.isDirectory) psiManager.findDirectory(virtualFile) else psiManager.findFile(virtualFile)
      icon = psi?.getIcon(Iconable.ICON_FLAG_READ_STATUS)
      icon = AbstractPsiBasedNode.patchIcon(localProject, icon, virtualFile)
      val rowIcon = (icon as? DeferredIcon)?.evaluate() as? RowIcon
      if (rowIcon != null) {
        val iconCount = rowIcon.iconCount
        for (i in 0 until iconCount) {
          val currentIcon = rowIcon.getIcon(i)
          val innerIcon = ((currentIcon as? LayeredIcon)?.getIcon(0) as? DeferredIcon)?.evaluate()
          if (innerIcon is EmptyIcon) isPlaceholder = true
          return@runReadAction
        }
      }
    }
    return if (!isPlaceholder && icon != null) icon!! else null
  }
}