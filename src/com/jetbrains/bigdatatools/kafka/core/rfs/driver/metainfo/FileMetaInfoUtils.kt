package io.confluent.kafka.core.rfs.driver.metainfo

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.util.SizeUtils
import io.confluent.kafka.core.util.TimeUtils
import io.confluent.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

object FileMetaInfoUtils {
  @Nls
  fun getFileModificationTime(fileInfo: FileInfo): String? {
    val unixTime = fileInfo.modificationTime
    return if (unixTime >= 0)
      TimeUtils.unixTimeToString(unixTime)
    else
      null
  }

  @Nls
  fun getFileSize(fileInfo: FileInfo): String? {
    val length = fileInfo.length
    return if (fileInfo.isFile && length >= 0)
      SizeUtils.toString(length)
    else
      null
  }

  @NlsSafe
  fun getPathType(path: RfsPath) = if (path.isDirectory)
    KafkaMessagesBundle.message("column.name.type.folder")
  else
    FileUtilRt.getExtension(path.name)

}