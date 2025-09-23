package io.confluent.kafka.core.rfs.editorviewer

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.util.invokeLater
import javax.swing.Icon

internal class RfsViewerEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile) = isSuitableFile(file)

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    //    return if (BdIdeRegistryUtil.isInternalFeaturesAvailable()) {
    return RfsTableViewerEditor(project, file)
    //}
    //else {
    //  RfsTreeTableViewerEditor(project, file)
    //}
  }

  override fun getEditorTypeId(): String = PROVIDER_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    const val PROVIDER_ID = "rfs-editor-producer"

    val RFS_EDITOR_VIEWER_DRIVER = Key<Driver>("RFS_EDITOR_VIEWER_DRIVER")
    val RFS_EDITOR_VIEWER_PATH = Key<RfsPath>("RFS_EDITOR_VIEWER_PATH")
    val RFS_EDITOR_VIEWER_SELECTED_PATH = Key<List<RfsPath>>("RFS_EDITOR_SELECTED_PATH")
    val RFS_EDITOR_VIEWER_ICON = Key<Icon>("RFS_EDITOR_VIEWER_ICON")
    val RFS_TREE_STATE = Key<TreeState>("RFS_TREE_STATE")

    fun isSuitableFile(file: VirtualFile) = file.getUserData(RFS_EDITOR_VIEWER_DRIVER) != null &&
                                            file.getUserData(RFS_EDITOR_VIEWER_PATH) != null

    fun createFileViewerEditor(project: Project, driver: Driver, rfsPath: RfsPath) = invokeLater {
      if (!driver.isRfsViewEditorAvailable) {
        return@invokeLater
      }

      val driverId = driver.getExternalId()
      val foundEditor = findFileEditors(project, driverId, rfsPath).firstOrNull()

      if (foundEditor != null) {
        val virtualFile = foundEditor.file
        if (virtualFile != null) {
          FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
        return@invokeLater
      }

      val openedEditors = openEditor(project, driver, rfsPath)
      openedEditors.forEach {
        Disposer.register(it) {
          RfsFileViewerSettings.getInstance().remove(driver, rfsPath)
        }
      }

      RfsFileViewerSettings.getInstance().add(driver, rfsPath)
    }

    fun closeFileViewerEditors(project: Project, connectionData: ConnectionData) = invokeLater {
      val foundEditor = findFileEditors(project, connectionData.innerId, null)
      val instance = FileEditorManager.getInstance(project)
      foundEditor.mapNotNull { it.file }.forEach {
        try {
          instance.closeFile(it)
        }
        catch (t: Throwable) {
          return@forEach
        }
      }
    }

    private fun findFileEditors(project: Project,
                                driverId: String,
                                rfsPath: RfsPath?) =
      FileEditorManager.getInstance(project).allEditors.filter {
        val virtualFile = it.file ?: return@filter false
        val editorDriver = virtualFile.getUserData(RFS_EDITOR_VIEWER_DRIVER) ?: return@filter false
        val editorPath = virtualFile.getUserData(RFS_EDITOR_VIEWER_PATH)
        editorDriver.getExternalId() == driverId && (rfsPath == null || editorPath == rfsPath)
      }

    private fun openEditor(project: Project, driver: Driver, rfsPath: RfsPath): Array<FileEditor> {
      val targetPath = if (rfsPath.isDirectory)
        rfsPath
      else
        rfsPath.parent ?: rfsPath

      val name = rfsPath.stringRepresentation().removeSuffix("/").ifBlank { driver.presentableName }
      val file = LightVirtualFile(name).apply {
        putUserData(RFS_EDITOR_VIEWER_PATH, targetPath)
        putUserData(RFS_EDITOR_VIEWER_DRIVER, driver)
        putUserData(RFS_EDITOR_VIEWER_ICON, driver.icon)
      }

      return FileEditorManager.getInstance(project).openFile(file, true)
    }
  }
}