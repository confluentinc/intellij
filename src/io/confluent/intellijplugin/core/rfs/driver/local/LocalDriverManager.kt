package io.confluent.intellijplugin.core.rfs.driver.local

import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Experiments.Companion.getInstance
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.settings.local.RfsLocalConnectionData
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Service
class LocalDriverManager : Disposable {
  private var drivers: Map<String, LocalDriver> = emptyMap()

  override fun dispose() {}

  fun createFileInfo(file: File): FileInfo {
    val driver = getDriver(file)

    return driver.getFileInfoFromIoFile(file)
  }

  fun getDrivers(): List<LocalDriver> {
    refreshIfRequiredRoots()
    return drivers.values.toList()
  }

  fun getDriver(file: File): LocalDriver {
    refreshIfRequiredRoots()
    return (drivers.entries.firstOrNull { file.absolutePath.startsWith(it.key) }?.value
            ?: error("Driver is not found for ${file.absolutePath}"))
  }

  private fun refreshIfRequiredRoots() {
    val roots = ArrayList<Path>()
    for (root in FileSystems.getDefault().rootDirectories) roots.add(root)
    if (WSLUtil.isSystemCompatible() && getInstance().isFeatureEnabled("wsl.p9.show.roots.in.file.chooser")) {
      try {
        val distributions = WslDistributionManager.getInstance().getInstalledDistributionsFuture()[200, TimeUnit.MILLISECONDS]
        for (distribution in distributions) roots.add(distribution.getUNCRootPath())
      }
      catch (e: Exception) {
        thisLogger().warn(e)
      }
    }

    val newRoots = roots.map { it.toFile() }
    if (drivers.keys.toList() == newRoots.map { it.absolutePath })
      return

    drivers.values.forEach { Disposer.dispose(it) }
    drivers = initDrivers(newRoots)
  }

  private fun initDrivers(newRoots: List<File>) = newRoots.associate {
    val driver = createUtilDriver(it, it.name)
    Disposer.register(this, driver)
    it.absolutePath to driver
  }


  companion object {
    val instance: LocalDriverManager
      get() = service()

    fun createUtilDriver(file: File, driverName: String = "temp-local-driver"): LocalDriver {
      if (file.isFile)
        error(KafkaMessagesBundle.message("util.driver.error.file", file.path))

      val data = RfsLocalConnectionData().apply {
        name = driverName
        innerId = "$driverName-id"
        rootPath = withSlashEnding(file.absolutePath)

      }
      return LocalDriver(data, null)
    }

    private fun withSlashEnding(prefix: String) = if (prefix.endsWith("/") || prefix.endsWith("}"))
      prefix
    else
      "$prefix/"
  }
}