package io.confluent.intellijplugin.client

import io.confluent.intellijplugin.core.connection.tunnel.model.ConnectionSshTunnelData
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class KafkaClientAutoReconnectTest {

    private fun clientWithTunnel(tunnel: ConnectionSshTunnelData) = KafkaClient(
        project = null,
        connectionData = mock<KafkaConnectionData> { on { getTunnelData() } doReturn tunnel },
        testConnection = false
    )

    @Test
    fun `should support auto-reconnect when no SSH tunnel is configured`() {
        assertTrue(clientWithTunnel(ConnectionSshTunnelData.DEFAULT).supportsAutoReconnect())
    }

    @Test
    fun `should not support auto-reconnect when the connection uses an SSH tunnel`() {
        val tunnel = ConnectionSshTunnelData(isEnabled = true, configId = "ssh-config")

        assertFalse(clientWithTunnel(tunnel).supportsAutoReconnect())
    }
}
