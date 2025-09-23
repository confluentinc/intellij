package io.confluent.kafka.core.rfs.fileType

import io.confluent.kafka.core.rfs.driver.FileInfo
import javax.swing.Icon

abstract class RfsFileType {
  abstract fun getIcon(locked: Boolean): Icon
  abstract fun isOfType(file: FileInfo): Boolean
  abstract fun getId(): String
  final override fun equals(other: Any?): Boolean = (other as? RfsFileType)?.getId() == getId()
  final override fun hashCode(): Int = getId().hashCode()
  override fun toString(): String = getId()

  companion object {
    fun getFileType(fileInfo: FileInfo): RfsFileType? =
      RfsFileTypeProvider.getAll().asSequence().map { it.getFileType() }.find { it.isOfType(fileInfo) }
  }
}