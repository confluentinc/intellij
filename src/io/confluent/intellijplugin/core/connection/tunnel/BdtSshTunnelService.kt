package io.confluent.intellijplugin.core.connection.tunnel

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ssh.SshSession
import com.intellij.ssh.connectionBuilder
import com.intellij.ssh.ui.unified.SshUiData
import com.intellij.util.containers.MultiMap
import com.intellij.util.net.NetUtils
import io.confluent.intellijplugin.core.connection.exception.BdtConfigurationException
import io.confluent.intellijplugin.core.connection.tunnel.model.ConnectionSshTunnelData
import io.confluent.intellijplugin.core.connection.tunnel.model.ConnectionSshTunnelInfo
import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT
import io.confluent.intellijplugin.core.util.BdtUrlUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object BdtSshTunnelService {
    /**
     * Need to control which connections use tunnels, The situation when several connections use the same tunnel is possible
     * For instance, it can be test connection + real connection
     */
    private val localPortReferences: MultiMap<Int, TunnelHandler> = MultiMap.createConcurrent()
    private val createdTunnels =
        ConcurrentHashMap<Pair<ConnectionSshTunnelInfo, TunnelHandler.CallerId>, TunnelHandler>()


    /**
     * The same object is returned when called with same [tunnelData], [uri] host and port, [connectionId] and [isTest] (weird contract).
     * Disposing of result object leads to destroying the tunnel only is there are no other (distinct) object pointing to the same tunnel.
     */
    fun createIfRequired(
        project: Project?,
        tunnelData: ConnectionSshTunnelData,
        uri: String,
        connectionId: String,
        isTest: Boolean
    ): UriTunnelHandler? {
        if (!tunnelData.isEnabled) return null

        if (uri.contains(",")) {
            throw BdtConfigurationException(
                KafkaMessagesBundle.message(
                    "connection.error.tunnel.should.be.created.just.for.one.url",
                    uri
                )
            )
        }
        val url = try {
            BdtUrlUtils.convertToUrlObject(uri)
        } catch (e: URISyntaxException) {
            null
        } catch (e: MalformedURLException) {
            null
        }
        val connectionSshTunnelInfo = ConnectionSshTunnelInfo(
            tunnelData.configId, remoteHost = url?.host ?: "", remotePort = url?.port ?: 0,
            localPort = tunnelData.localPort
        )
        return createIfRequiredInternal(project, connectionSshTunnelInfo, connectionId, isTest)?.forUri(uri)
    }

    /**
     * The same object is returned when called with same [connectionSshTunnelInfo], [connectionId] and [isTest] (weird contract).
     * Disposing of result object leads to destroying the tunnel only is there are no other (distinct) object pointing to the same tunnel.
     */
    fun createIfRequiredInternal(
        project: Project?,
        connectionSshTunnelInfo: ConnectionSshTunnelInfo?,
        connectionId: String,
        isTest: Boolean
    ): TunnelHandler? {
        if (connectionSshTunnelInfo == null)
            return null

        if (connectionSshTunnelInfo.remotePort == -1 || connectionSshTunnelInfo.remoteHost.isBlank())
            error("Cannot create tunnel to $connectionSshTunnelInfo")

        val callerId = TunnelHandler.CallerId(connectionId, isTest)

        val existingTunnel = createdTunnels[connectionSshTunnelInfo to callerId]
        if (existingTunnel != null && existingTunnel.isConnected()) {
            return existingTunnel
        }
        val session = getOrCreateSession(connectionSshTunnelInfo, project)
        val localPort = synchronized(session) {
            getOrCreateLocalTunnel(session, connectionSshTunnelInfo)
        }
        if (existingTunnel != null) {
            removeTunnel(existingTunnel)
        }
        val portForwardingListMember = session.getPortForwardingList().first { it.startsWith("$localPort:") }
        return createdTunnels.compute(connectionSshTunnelInfo to callerId) { _, v ->
            if (v == null || v == existingTunnel) {
                TunnelHandler(
                    localPort,
                    connectionSshTunnelInfo,
                    callerId,
                    session,
                    portForwardingListMember
                ).also { tunnelHandler ->
                    localPortReferences.putValue(localPort, tunnelHandler)
                }
            } else v
        }
    }

    private fun getOrCreateSession(tunnelInfo: ConnectionSshTunnelInfo, project: Project?): SshSession {
        val sshConfig = tunnelInfo.sshConfig
        val connectionBuilder = SshUiData(sshConfig).connectionBuilder(project)
        return try {
            connectionBuilder.withConnectionTimeout(RFS_DEFAULT_TIMEOUT.toLong(), TimeUnit.MILLISECONDS).connect()
        } catch (t: Throwable) {
            logger.info("Session creation error for ${sshConfig}", t)
            throw t
        }
    }


    private fun getOrCreateLocalTunnel(session: SshSession, tunnelInfo: ConnectionSshTunnelInfo): Int {
        if (!session.isConnected) {
            error("SSH Session is disconnected during creation local tunnel")
        }
        val foundLocalPort = findLocalPortForSession(session, tunnelInfo)

        return foundLocalPort ?: let {
            if (tunnelInfo.localPort != null) {
                session.addLocalTunnel(tunnelInfo.localPort, tunnelInfo.remoteHost, tunnelInfo.remotePort)
                tunnelInfo.localPort
            } else {
                session.addLocalTunnelWithRandomLocalPort(tunnelInfo.remoteHost, tunnelInfo.remotePort)
            }
        }
    }


    private fun findLocalPortForSession(session: SshSession, tunnelInfo: ConnectionSshTunnelInfo): Int? {
        val foundPortForwarding = session.getPortForwardingList().find {
            val (localPort, curRemoteHost, curRemotePort) = it.split(":")

            curRemoteHost == tunnelInfo.remoteHost && curRemotePort.toInt() == tunnelInfo.remotePort &&
                    (tunnelInfo.localPort == null || tunnelInfo.localPort == localPort.toIntOrNull())
        }

        return foundPortForwarding?.split(":")?.first()?.toInt()
    }

    fun removeTunnel(tunnelHandler: TunnelHandler) {
        createdTunnels.remove(tunnelHandler.tunnelInfo to tunnelHandler.callerId)
        localPortReferences.remove(tunnelHandler.localPort, tunnelHandler)
        synchronized(tunnelHandler.session) {
            if (localPortReferences.get(tunnelHandler.localPort).isEmpty()) {
                tunnelHandler.session.removeLocalTunnel(tunnelHandler.localPort)
            }
        }
    }

    private val logger = Logger.getInstance(this::class.java)
}

class TunnelHandler internal constructor(
    val localPort: Int,
    val tunnelInfo: ConnectionSshTunnelInfo,
    val callerId: CallerId,
    val session: SshSession,
    private val portForwardingListMember: String
) : Disposable {
    data class CallerId(val id: String, val isTest: Boolean)

    fun isConnected(): Boolean {
        return session.isConnected && session.getPortForwardingList().contains(portForwardingListMember)
    }

    override fun dispose() {
        BdtSshTunnelService.removeTunnel(this)
    }

    fun forUri(originalUri: String): UriTunnelHandler {
        val urlObject = BdtUrlUtils.convertToUrlObject(originalUri)

        val tunnelledUrl = URL(
            urlObject.protocol, NetUtils.getLocalHostString(), localPort, urlObject.file,
            BdtUrlUtils.getUrlHandler(BdtUrlUtils.getProtocol(urlObject.toExternalForm()))
        ).toExternalForm()
        return UriTunnelHandler(tunnelledUrl, originalUri, this)
    }
}

class UriTunnelHandler(
    val tunnelledUri: String,
    @Suppress("unused") val originalUri: String,
    tunnelHandler: TunnelHandler
) : Disposable by tunnelHandler
