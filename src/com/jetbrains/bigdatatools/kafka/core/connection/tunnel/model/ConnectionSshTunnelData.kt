package com.jetbrains.bigdatatools.kafka.core.connection.tunnel.model

import com.intellij.openapi.project.Project
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.config.unified.SshConfigManager
import com.jetbrains.bigdatatools.kafka.core.connection.exception.SshConfigNotFoundException
import java.io.Serializable

/**
 * Used as field type for compatibility reasons, deprecated fields are cleared after migration (migration moves data to url).
 * In interfaces please use [ConnectionSshTunnelData] or [ConnectionSshTunnelInfo].
 */
data class ConnectionSshTunnelDataLegacy(
  val isEnabled: Boolean,
  val configId: String,
  @Deprecated("Write remote host:port in uri section") val remoteHost: String,
  @Deprecated("Write remote host:port in uri section") val remotePort: Int,
  val localPort: Int? = null,
) : Serializable {
  fun getSshConfig(project: Project?) = SshConfigManager.getInstance(project).findConfigById(configId)

  companion object {
    val DEFAULT: ConnectionSshTunnelDataLegacy = ConnectionSshTunnelDataLegacy(isEnabled = false,
                                                                               configId = "",
                                                                               remoteHost = "",
                                                                               remotePort = -1)

    const val serialVersionUID: Long = -8632095507356038549
  }
}

/**
 * Represents configurable from UI tunnel data. Config id is displayed and stored even for disabled connection for UX purposes.
 */
data class ConnectionSshTunnelData(val isEnabled: Boolean, val configId: String, val localPort: Int? = null) {
  constructor(configId: String) : this(true, configId)
  companion object {
    val DEFAULT = ConnectionSshTunnelData(isEnabled = false, configId = "")
  }
}

/**
 * Represents tunnel data ready for creating an SSH tunnel (see [com.jetbrains.bigdatatools.kafka.core.connection.tunnel.BdtSshTunnelService]). Can be formed from URI and [ConnectionSshTunnelData]
 */
data class ConnectionSshTunnelInfo(val sshConfig: SshConfig, val remoteHost: String, val remotePort: Int, val localPort: Int? = null) {
  constructor(configId: String, remoteHost: String, remotePort: Int, project: Project? = null,
              localPort: Int? = null) :
    this(SshConfigManager.getInstance(project).findConfigById(configId).let { sshConfig ->
      if (sshConfig == null) {
        throw SshConfigNotFoundException(configId)
      }
      sshConfig
    }, remoteHost, remotePort, localPort = localPort)
}
