package com.jetbrains.bigdatatools.kafka.core.rfs.exception

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfoBase

class RfsPermissionException(val fileInfo: FileInfoBase, override val cause: Throwable? = null) : RuntimeException() {
  override val message: String
    get() = "Permission denied. Allowed permissions: ${fileInfo.permission?.printString() ?: "null"}"
}