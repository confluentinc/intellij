package com.jetbrains.bigdatatools.kafka.core.rfs.driver.copyhandler

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ExportFormat
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.task.RfsCopyMoveTask
import com.jetbrains.bigdatatools.kafka.core.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.kafka.core.util.InternalFeature
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