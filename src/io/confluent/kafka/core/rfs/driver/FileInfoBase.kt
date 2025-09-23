@file:Suppress("DEPRECATION")

package io.confluent.kafka.core.rfs.driver

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.util.PathUtil
import io.confluent.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import io.confluent.kafka.core.rfs.driver.fileinfo.SafeResult
import io.confluent.kafka.core.rfs.driver.local.LocalDriver
import io.confluent.kafka.core.rfs.driver.task.RemoteFsDeleteTask
import io.confluent.kafka.core.rfs.driver.task.RemoteFsMoveTask
import io.confluent.kafka.core.rfs.driver.task.RemoteFsTask
import io.confluent.kafka.core.rfs.driver.task.RfsCopyMoveTask
import io.confluent.kafka.core.rfs.exception.RfsPermissionException
import io.confluent.kafka.core.rfs.util.withSlash
import java.io.InputStream

abstract class FileInfoBase : FileInfo {
  override val isSynthetic: Boolean get() = false

  override val name: String
    get() = path.name

  override val permission: FilePermission? = null
  override val isCopySupport: Boolean = true

  override fun toString(): String = path.stringRepresentation()

  override fun equals(other: Any?): Boolean =
    if (other !is FileInfo) false
    else !Disposer.isDisposed(driver) &&
         !Disposer.isDisposed(other.driver) &&
         other.path == path &&
         other.name == name &&
         other.driver.getExternalId() == driver.getExternalId()

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + Disposer.isDisposed(driver).hashCode()
    result = 31 * result + driver.getExternalId().hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + path.hashCode()
    return result
  }

  override val isDirectory: Boolean get() = path.isDirectory

  override val isFile: Boolean get() = path.isFile

  override val isSymbolicLink: Boolean get() = false

  override fun nameForDriver(driver: Driver, exportFormat: ExportFormat?): String {
    val correctName = if (driver is LocalDriver) PathUtil.suggestFileName(name, true, true)
    else
      name
    return if (isDirectory)
      correctName.withSlash()
    else
      correctName
  }

  final override fun delete(): SafeResult<Unit> = deleteAsync().map { it.run(EmptyProgressIndicator()) }


  final override fun renameAsync(newPath: RfsPath, overwrite: Boolean): SafeResult<RemoteFsMoveTask> = safeExecute {
    val innerTask = doRenameAsync(newPath, overwrite)
    return@safeExecute object : RemoteFsMoveTask(this, newPath) {
      override fun isNeedPrecalculate(): Boolean = innerTask.isNeedPrecalculate()

      override fun moveUserInfoMessage() = innerTask.moveUserInfoMessage()

      override fun run(context: RfsCopyMoveContext) {
        innerTask.run(context)
        driver.fileInfoManager.waitAppear(newPath)
        driver.fileInfoManager.waitDisappear(path)
      }
    }
  }

  abstract fun doRenameAsync(newPath: RfsPath, overwrite: Boolean): RfsCopyMoveTask

  final override fun deleteAsync(): SafeResult<RemoteFsTask> = safeExecute {
    val innerTask = doDeleteAsync()
    return@safeExecute object : RemoteFsDeleteTask(path) {
      override fun run(indicator: ProgressIndicator) {
        innerTask.run(indicator)
        if (!indicator.isCanceled) {
          driver.fileInfoManager.waitDisappear(path)
        }
      }
    }
  }

  abstract fun doDeleteAsync(): RemoteFsTask

  override fun readStream(offset: Long, exportFormat: ExportFormat?): SafeResult<InputStream> = safeExecute {
    try {
      doGetReadStream(offset, exportFormat)
    }
    catch (e: Throwable) {
      if (permission?.readable == false) {
        throw RfsPermissionException(this, e)
      }
      else throw e
    }
  }

  abstract fun doGetReadStream(offset: Long, exportFormat: ExportFormat?): InputStream

  protected fun <T> safeExecute(task: () -> T) =
    driver.safeExecutor.asyncInterruptible(taskName = null) {
      task()
    }.blockingGet()

}


fun FileInfo.getPresentableName(): String = presentableText(driver, path)

fun FileInfo.parentPath(): RfsPath = path.parent ?: driver.root

fun FileInfo.writeLocked(): Boolean = permission?.writeable == false

fun FileInfo.readLocked(): Boolean = permission?.readable == false

fun FileInfo.cachedChildren(): Iterable<FileInfo>? = driver.fileInfoManager.getCachedChildrenOrReload(path)?.result?.fileInfos