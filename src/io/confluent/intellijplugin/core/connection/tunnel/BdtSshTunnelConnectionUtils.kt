package io.confluent.intellijplugin.core.connection.tunnel

import io.confluent.intellijplugin.core.connection.tunnel.model.ConnectionSshTunnelDataLegacy
import io.confluent.intellijplugin.core.util.BdtUrlUtils
import java.net.URL

object BdtSshTunnelConnectionUtils {
    /**
     * Transform settings fields from form when tunnel info is stored as data class to form where ssh tunnel config is url+tunnelInfo
     */
    @Deprecated("used only for migration")
    fun transformToConfigVersion2(
        uri: String,
        tunnel: ConnectionSshTunnelDataLegacy
    ): Pair<String, ConnectionSshTunnelDataLegacy> {
        var oldRemoteHost = tunnel.remoteHost
        //Migrate host from localhost to ssh config host
        if (oldRemoteHost == "127.0.0.1" || oldRemoteHost == "localhost") {
            val host = tunnel.getSshConfig(null)?.host
            if (host != null)
                oldRemoteHost = host
        }

        if (!tunnel.isEnabled || oldRemoteHost.isEmpty())
            return uri to tunnel

        val original = BdtUrlUtils.convertToUrlObject(uri)
        val urlHandler = BdtUrlUtils.getUrlHandler(original.protocol)
        val newUrl = URL(original.protocol, oldRemoteHost, tunnel.remotePort, original.file, urlHandler)

        return newUrl.toExternalForm().removeSuffix("/") to tunnel.copy(remotePort = -1, remoteHost = "")
    }
}