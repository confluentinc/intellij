package com.jetbrains.bigdatatools.kafka.core.rfs.driver.task

import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.RfsCopyPasteParams
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.RfsCopyPasteUtil
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle


abstract class BaseRfsCopyMoveTask(val rootFromInfo: FileInfo,
                                   toPath: RfsPath,
                                   toDriver: Driver,
                                   private val skipIfCopyChildIsNotSupported: Boolean = false,
                                   val additionalParams: Map<String, Any> = emptyMap()) :
  RfsCopyMoveTask(rootFromInfo.driver,
                  rootFromInfo.path, toDriver,
                  toPath) {

  override fun run(context: RfsCopyMoveContext) = copy(context, rootFromInfo, rootToPath, toDriver)

  private fun copy(context: RfsCopyMoveContext, fromInfo: FileInfo, toPath: RfsPath, toDriver: Driver) {
    if (context.isCanceled)
      return

    if (toPath == fromInfo.path && fromInfo.driver == toDriver && additionalParams[RfsCopyPasteParams.FORCE_OVERWRITE] != true)
      return

    if (skipIfCopyChildIsNotSupported && !fromInfo.isCopySupport && fromInfo != this.rootFromInfo)
      return

    if (!fromInfo.isCopySupport)
      throw failedToCopyException(KafkaMessagesBundle.message("copy.is.not.supported", fromInfo.externalPath))

    doCopy(fromInfo, context, toPath, toDriver)
  }

  private fun doCopy(fromInfo: FileInfo,
                     context: RfsCopyMoveContext,
                     toPath: RfsPath,
                     toDriver: Driver) = if (fromInfo.isFile) {
    copyFile(context, fromInfo, toPath, toDriver)
  }
  else
    copyDir(context, fromInfo, toPath, toDriver)

  abstract fun copyFile(context: RfsCopyMoveContext,
                        fromInfo: FileInfo,
                        toPath: RfsPath,
                        toDriver: Driver)


  open fun copyDir(context: RfsCopyMoveContext,
                   fromInfo: FileInfo,
                   toPath: RfsPath,
                   toDriver: Driver) {
    val children = fromInfo.driver.listStatus(fromInfo.path, force = true).resultOrThrow().fileInfos ?: emptyList()

    if (context.isCanceled)
      return

    children.forEach {
      val childPath = RfsCopyPasteUtil.getCorrectTargetPath(it, toPath, toDriver, exportFormat = null)
      copy(context, it, childPath, toDriver)
    }
  }
}