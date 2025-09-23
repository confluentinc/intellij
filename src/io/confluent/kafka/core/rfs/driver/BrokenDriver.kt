package io.confluent.kafka.core.rfs.driver

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.icons.RfsIcons
import io.confluent.kafka.core.settings.connections.ConnectionData
import java.io.OutputStream
import javax.swing.Icon

class BrokenDriver(
  override val project: Project?,
  delegateName: String,
  private val underlyingId: String,
  delegateIcon: Icon = RfsIcons.DIRECTORY_ICON,
  override val connectionData: ConnectionData,
  val e: Exception
) : DriverBase() {
  override val presentableName: String = delegateName

  override val icon: Icon = delegateIcon

  override fun doCheckAvailable(): ReadyConnectionStatus {
    return checkHomeInfo()
  }

  override fun doRefreshConnection(calledByUser: Boolean) {
    throw e
  }

  override fun doGetHomeUri(): String = throw e

  override fun doListStatus(path: RfsPath): List<FileInfo> = throw e

  override fun doGetFileStatus(path: RfsPath): FileInfo = throw e

  override fun doMkdir(path: RfsPath) = throw e

  override fun doCreateWriteStream(rfsPath: RfsPath, overwrite: Boolean, create: Boolean): OutputStream = throw e

  override fun getExternalId(): String = underlyingId

  override fun validatePath(path: RfsPath): String? = null

  object BrokenRfsPath : RfsPath(emptyList(), true)

  override fun createRfsPath(path: String): RfsPath = BrokenRfsPath
}