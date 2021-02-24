package com.jetbrains.bigdatatools.kafka

import com.jetbrains.bigdatatools.rfs.driver.DriverBase
import com.jetbrains.bigdatatools.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.rfs.driver.RfsPath
import javax.swing.Icon

class KafkaDriver : DriverBase() {
  override val presentableName: String
    get() = TODO("Not yet implemented")

  override fun getExternalId(): String {
    TODO("Not yet implemented")
  }

  override fun validatePath(path: RfsPath): String? {
    TODO("Not yet implemented")
  }

  override val icon: Icon
    get() = TODO("Not yet implemented")

  override fun doRefreshConnection() {
    TODO("Not yet implemented")
  }

  override fun doCreate(path: RfsPath, overwrite: Boolean): FileInfo? {
    TODO("Not yet implemented")
  }

  override fun doGetHomeUri(): String {
    TODO("Not yet implemented")
  }

  override fun doListStatus(path: RfsPath): Iterable<FileInfo>? {
    TODO("Not yet implemented")
  }

  override fun doGetFileStatus(path: RfsPath, force: Boolean): FileInfo? {
    TODO("Not yet implemented")
  }

  override fun doMkdir(path: RfsPath): Boolean {
    TODO("Not yet implemented")
  }
}