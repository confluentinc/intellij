package io.confluent.kafka.rfs

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import io.confluent.kafka.core.rfs.driver.ExportFormat
import io.confluent.kafka.core.rfs.driver.FileInfoBase
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.task.RemoteFsDeleteTask
import io.confluent.kafka.core.rfs.driver.task.RfsCopyMoveTask
import java.io.InputStream

class KafkaFileInfo(override val driver: KafkaDriver, override val path: RfsPath) : FileInfoBase() {
  override val externalPath: String = path.stringRepresentation()
  override val length: Long = -1
  override val modificationTime: Long = -1

  override val isCopySupport: Boolean = false
  override val isActionDeleteSupport: Boolean = false
  override val isMoveSupport: Boolean = false

  override fun isMetaInfoSupport(): Boolean = false

  override fun doDeleteAsync() = object : RemoteFsDeleteTask(path) {
    override fun run(indicator: ProgressIndicator) {
      when (path.parent) {
        KafkaDriver.topicPath -> driver.dataManager.deleteTopic(listOf(path.name))
        KafkaDriver.schemasPath -> runBlockingCancellable {
          driver.dataManager.deleteSchema(path.name).join()
        }
      }
    }
  }

  override fun doRenameAsync(newPath: RfsPath, overwrite: Boolean): RfsCopyMoveTask {
    TODO("Not yet implemented")
  }

  override fun doGetReadStream(offset: Long, exportFormat: ExportFormat?): InputStream {
    TODO("Not yet implemented")
  }
}