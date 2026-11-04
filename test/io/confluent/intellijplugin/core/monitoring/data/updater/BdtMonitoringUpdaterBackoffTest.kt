package io.confluent.intellijplugin.core.monitoring.data.updater

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.connection.MonitoringClient
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.model.DataModel
import io.confluent.intellijplugin.core.monitoring.data.storage.DataModelStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@TestApplication
class BdtMonitoringUpdaterBackoffTest {

    /** Real connection-state machine; [failing] makes connect/checkConnection throw. */
    private class FakeClient : MonitoringClient(null) {
        var failing = false

        override fun getRealUri() = "fake://cluster"
        override fun checkConnectionInner() = failIfNeeded()
        override fun connectInner(calledByUser: Boolean) = failIfNeeded()
        override fun dispose() {}

        private fun failIfNeeded() {
            if (failing) throw IllegalStateException("broker down")
        }
    }

    /** Model that always requires an update (it has a listener) and fails while [failing] is set. */
    private class FakeModel : DataModel<String>() {
        var failing = false
        var onUpdate: () -> Unit = {}

        override val updater: (DataModel<*>) -> Pair<String, Boolean> = {
            onUpdate()
            if (failing) throw IllegalStateException("fetch failed")
            "data" to false
        }

        init {
            addListener(object : DataModelListener {})
        }
    }

    private val client = FakeClient()
    private val model = FakeModel()
    private var refreshedModels: List<DataModel<*>> = emptyList()

    private val settings = mock<IntervalUpdateSettings> {
        on { dataUpdateIntervalMillis } doReturn 0 // don't arm the auto-refresh alarm
    }
    private val dataManager = mock<MonitoringDataManager> {
        on { client } doReturn client
        on { settings } doReturn settings
    }
    private lateinit var updater: BdtMonitoringUpdater

    @BeforeEach
    fun setUp() {
        updater = BdtMonitoringUpdater(dataManager)
        // Registered while empty so the registration-time async refresh is a no-op.
        updater.registryStorage(mock<DataModelStorage> {
            on { getModelsForRefresh() } doAnswer { refreshedModels }
        })
        refreshedModels = listOf(model)
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(updater)
    }

    private fun connect(failing: Boolean) {
        client.failing = failing
        runCatching { client.connect(calledByUser = false) }
    }

    @Nested
    inner class `counts a failed cycle` {

        @Test
        fun `when the client already has a connection error and the refresh skips the check`() {
            connect(failing = true)

            updater.reloadAll(checkConnection = false)

            assertEquals(1, updater.consecutiveFailureCount)
            assertNotNull(model.error)
        }

        @Test
        fun `when re-probing a client that already has a connection error still fails`() {
            connect(failing = true)

            updater.reloadAll(checkConnection = true)

            assertEquals(1, updater.consecutiveFailureCount)
            assertNotNull(model.error)
            assertFalse(client.isConnected())
        }

        @Test
        fun `when the connection check fails`() {
            connect(failing = false)
            client.failing = true

            updater.reloadAll(checkConnection = true)

            assertEquals(1, updater.consecutiveFailureCount)
            assertNotNull(model.error)
        }

        @Test
        fun `and accumulates across consecutive failed cycles`() {
            connect(failing = true)

            repeat(3) { updater.reloadAll(checkConnection = true) }

            assertEquals(3, updater.consecutiveFailureCount)
        }
    }

    @Nested
    inner class `does not count` {

        @Test
        fun `a model update failure while the connection is healthy`() {
            connect(failing = false)
            model.failing = true

            repeat(3) { updater.reloadAll(checkConnection = true) }

            assertEquals(0, updater.consecutiveFailureCount)
            assertNotNull(model.error)
        }

        @Test
        fun `a cycle cancelled mid-refresh`() {
            connect(failing = true)
            repeat(2) { updater.reloadAll(checkConnection = true) }

            client.failing = false
            model.onUpdate = { updater.cancelCurrent() }
            updater.reloadAll(checkConnection = true)

            assertEquals(2, updater.consecutiveFailureCount)
        }
    }

    @Nested
    inner class `resets the counter` {

        @Test
        fun `when auto-refresh re-probes a failed connection that has recovered`() {
            connect(failing = true)
            repeat(2) { updater.reloadAll(checkConnection = true) }
            assertEquals(2, updater.consecutiveFailureCount)

            client.failing = false
            updater.reloadAll(checkConnection = true)

            assertEquals(0, updater.consecutiveFailureCount)
            assertTrue(client.isConnected())
            assertNull(model.error)
            assertEquals("data", model.data)
        }

        @Test
        fun `when there is nothing to update`() {
            connect(failing = true)
            updater.reloadAll(checkConnection = false)

            refreshedModels = emptyList()
            updater.reloadAll(checkConnection = false)

            assertEquals(0, updater.consecutiveFailureCount)
        }
    }
}
