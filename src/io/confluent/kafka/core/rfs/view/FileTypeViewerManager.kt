package io.confluent.kafka.core.rfs.view

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.manager.DriverManager
import io.confluent.kafka.core.util.ConnectionUtil

@Service(Service.Level.PROJECT)
class FileTypeViewerManager(val project: Project) {

  fun openSearchResult(connectionId: String, rfsPath: RfsPath) {
    val driver = DriverManager.getDriverById(project, connectionId) ?: return

    //val fileTypeViewer = RfsFileTypeViewer.getSuitableFor(driver, rfsPath) ?: RfsFileTypeViewer.defaultViewer
    if (rfsPath.isDirectory)
      ConnectionUtil.goToConnection(project, driver.getExternalId(), rfsPath)
    //else
    //  fileTypeViewer.openSearchResult(project, driver, rfsPath)
  }

  companion object {
    fun getInstance(project: Project): FileTypeViewerManager = project.getService(FileTypeViewerManager::class.java)
  }
}