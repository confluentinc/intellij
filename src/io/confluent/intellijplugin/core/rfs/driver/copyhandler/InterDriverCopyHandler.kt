package io.confluent.intellijplugin.core.rfs.driver.copyhandler

import com.intellij.openapi.extensions.ExtensionPointName
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.ExportFormat
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.task.RfsCopyMoveTask
import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil
import io.confluent.intellijplugin.core.util.InternalFeature
import org.jetbrains.annotations.Nls

interface InterDriverCopyHandler {
  fun canHandle(fromInfo: FileInfo, toDriver: Driver): Boolean

  fun userInfoMessage(): @Nls String? = null

  fun correctPathForTarget(fromInfo: FileInfo, toPath: RfsPath, toDriver: Driver, exportFormat: ExportFormat?): RfsPath? = null

  fun buildCopyTask(fromInfo: FileInfo,
                    toPath: RfsPath,
                    toDriver: Driver,
                    exportFormat: ExportFormat?,
                    additionalParams: Map<String, Any>): RfsCopyMoveTask

  companion object {
    val EP_NAME = ExtensionPointName.create<InterDriverCopyHandler>("com.intellij.bigdatatools.rfs.driver.interDriverCopyHandler")

    fun getAll() = if (BdIdeRegistryUtil.isInternalFeaturesAvailable())
      EP_NAME.extensionList
    else
      EP_NAME.extensionList.filter { it !is InternalFeature }

    fun getAllSuitable(fromInfo: FileInfo, toDriver: Driver) =
      getAll().filter { it.canHandle(fromInfo, toDriver) }

    fun hasSuitable(fromInfo: FileInfo, toDriver: Driver) =
      getAllSuitable(fromInfo, toDriver).isNotEmpty()

    fun getCorrectTargetPath(fromInfo: FileInfo,
                             toParentPath: RfsPath,
                             toDriver: Driver,
                             exportFormat: ExportFormat?): RfsPath? {
      val allSuitable = getAllSuitable(fromInfo, toDriver)
      return allSuitable.firstNotNullOfOrNull { it.correctPathForTarget(fromInfo, toParentPath, toDriver, exportFormat) }
    }
  }
}