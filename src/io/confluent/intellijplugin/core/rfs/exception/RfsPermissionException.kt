package io.confluent.intellijplugin.core.rfs.exception

import io.confluent.intellijplugin.core.rfs.driver.FileInfoBase

class RfsPermissionException(val fileInfo: FileInfoBase, override val cause: Throwable? = null) : RuntimeException() {
  override val message: String
    get() = "Permission denied. Allowed permissions: ${fileInfo.permission?.printString() ?: "null"}"
}