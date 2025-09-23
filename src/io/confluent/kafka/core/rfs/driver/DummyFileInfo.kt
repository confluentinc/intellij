package io.confluent.kafka.core.rfs.driver

import io.confluent.kafka.core.rfs.driver.fileinfo.ErrorResult
import io.confluent.kafka.core.rfs.driver.fileinfo.SafeResult
import io.confluent.kafka.core.rfs.driver.task.RemoteFsMoveTask
import io.confluent.kafka.core.rfs.driver.task.RemoteFsTask
import io.confluent.kafka.util.KafkaMessagesBundle
import java.io.InputStream

class DummyFileInfo(override val name: String,
                    override val path: RfsPath,
                    override val externalPath: String,
                    override val driver: Driver) : FileInfo {
  override val isCopySupport: Boolean = false
  override val isMoveSupport: Boolean = false
  override val isActionDeleteSupport: Boolean = false

  private fun delegateStatus() = driver.getFileStatus(path).result?.takeIf { it !is DummyFileInfo }

  private fun noDelegateResult() = IllegalStateException(KafkaMessagesBundle.message("rfs.dummy.info.no.delegate", externalPath))

  //TODO do we want to successfully construct tasks when there is no delegate status?
  override fun deleteAsync(): SafeResult<RemoteFsTask> = delegateStatus()?.deleteAsync() ?: ErrorResult(noDelegateResult())

  override fun renameAsync(newPath: RfsPath, overwrite: Boolean): SafeResult<RemoteFsMoveTask> = delegateStatus()?.renameAsync(newPath,
                                                                                                                               overwrite)
                                                                                                 ?: ErrorResult(noDelegateResult())

  override fun isMkDirSupport() = false

  override fun isCreateFileSupport() = false

  override fun isMetaInfoSupport() = false

  override val isSynthetic: Boolean get() = true

  override val isDirectory: Boolean get() = false

  override val isFile: Boolean get() = true

  override val isSymbolicLink: Boolean get() = false

  override fun delete(): SafeResult<Unit> = delegateStatus()?.delete() ?: ErrorResult(noDelegateResult())

  override fun readStream(offset: Long, exportFormat: ExportFormat?): SafeResult<InputStream> =
    delegateStatus()?.readStream(offset, exportFormat) ?: ErrorResult(noDelegateResult())

  override fun nameForDriver(driver: Driver, exportFormat: ExportFormat?): String = name

  override val length: Long = 0L
  override val modificationTime: Long = 0L
  override val permission: FilePermission = FilePermission(true, true, true)
}