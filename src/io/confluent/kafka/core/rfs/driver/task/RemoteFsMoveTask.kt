package io.confluent.kafka.core.rfs.driver.task

import io.confluent.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath

abstract class RemoteFsMoveTask(fromInfo: FileInfo,
                                toPath: RfsPath) : BaseRfsCopyMoveTask(fromInfo, toPath, fromInfo.driver) {
  override fun copyFile(context: RfsCopyMoveContext, fromInfo: FileInfo, toPath: RfsPath, toDriver: Driver) {
    TODO("Not yet implemented")
  }
}