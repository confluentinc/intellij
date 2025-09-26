package io.confluent.intellijplugin.core.connection.tunnel.model

import com.intellij.openapi.diagnostic.Logger
import io.confluent.intellijplugin.core.connection.tunnel.BdtSshTunnelConnectionUtils
import kotlin.reflect.KMutableProperty0

interface TunnelableData {
  /**
   * Internal field kept for compatibility and serialization reasons. Use [getTunnelData] and [setTunnelData] everywhere.
   */
  var tunnel: ConnectionSshTunnelDataLegacy

  /**
   * Used for initializing UI components, after modification passed into [setTunnelData].
   * Currently [getTunnelDataOrDefault] is called instead for more exception-safety (todo: why?)
   */
  fun getTunnelData(): ConnectionSshTunnelData {
    return ConnectionSshTunnelData(tunnel.isEnabled, tunnel.configId, localPort = tunnel.localPort)
  }

  fun setTunnelData(info: ConnectionSshTunnelData) {
    tunnel = ConnectionSshTunnelDataLegacy(info.isEnabled, info.configId, remoteHost = "", remotePort = -1, localPort = info.localPort)
  }
}

@Suppress("DEPRECATION")
fun TunnelableData.migrateTunnel(uri: KMutableProperty0<String>) {
  try {
    val (newUri, newTunnel) = BdtSshTunnelConnectionUtils.transformToConfigVersion2(uri.get(), tunnel)
    uri.set(newUri)
    tunnel = newTunnel
  }
  catch (t: Throwable) {
    val logger = Logger.getInstance(this::class.java)
    logger.warn(t)
  }
}

fun TunnelableData.getTunnelDataOrDefault(): ConnectionSshTunnelData = try {
  getTunnelData()
}
catch (t: Throwable) {
  val logger = Logger.getInstance(TunnelableData::class.java)
  logger.warn("Cannot parse tunnel data", t)
  ConnectionSshTunnelData(false, "")
}