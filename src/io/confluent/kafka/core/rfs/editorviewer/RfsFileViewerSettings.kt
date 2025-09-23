package io.confluent.kafka.core.rfs.editorviewer

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager

@Service
@State(name = "RfsFileViewerSettings", storages = [Storage("rfsFileViewerSettings.xml")])
class RfsFileViewerSettings : PersistentStateComponent<RfsFileViewerSettings> {

  companion object {
    fun getInstance(): RfsFileViewerSettings = service()
  }

  data class RfsFileViewerInfo(var driverId: String = "", // Actually - connection ID
                               var pathElements: List<String> = emptyList(),
                               var pathIsDirectory: Boolean = true,
                               var hiddenColumn: List<String> = emptyList())

  var tabsInfo = mutableListOf<RfsFileViewerInfo>()

  // Map <ConnectionId -> List of Hidden columns names>
  private var visibleColumns = mutableMapOf<String, MutableList<String>>()

  var showDetailsPanel = true

  fun isColumnVisible(driver: Driver, columnName: String): Boolean = getDriverColumns(driver).contains(columnName)

  fun hasVisibleColumns(driver: Driver) = getDriverColumns(driver).isNotEmpty()

  fun hideColumn(driver: Driver, columnName: String) {
    getDriverColumns(driver).remove(columnName)
  }

  fun showColumn(driver: Driver, columnName: String) {
    getDriverColumns(driver).add(columnName)
  }

  fun remove(driver: Driver, rfsPath: RfsPath) {
    tabsInfo.removeIf {
      it.driverId == driver.getExternalId() && RfsPath(it.pathElements, it.pathIsDirectory) == rfsPath
    }
  }

  fun add(driver: Driver, rfsPath: RfsPath) {
    if (!has(driver, rfsPath)) {
      tabsInfo.add(RfsFileViewerInfo(driver.getExternalId(), rfsPath.elements, rfsPath.isDirectory))
    }
  }

  fun has(driver: Driver, rfsPath: RfsPath) = tabsInfo.firstOrNull {
    it.driverId == driver.getExternalId() && RfsPath(it.pathElements, it.pathIsDirectory) == rfsPath
  } != null

  override fun getState() = this

  override fun loadState(state: RfsFileViewerSettings) {
    try {
      XmlSerializerUtil.copyBean(state, this)
    }
    catch (ignore: Exception) {

    }
  }

  private fun getDriverColumns(driver: Driver) = visibleColumns.getOrPut(driver.connectionDataClassName()) {
    driver.getMetaInfoProvider().getDefaultTableColumns().toMutableList()
  }

  private fun Driver.connectionDataClassName(): String =
    RfsConnectionDataManager.instance?.findConnectionInAllProjects(getExternalId())?.javaClass?.name ?: javaClass.name
}