package io.confluent.intellijplugin.core.monitoring.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.connection.MonitoringClient
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.core.monitoring.data.updater.MonitoringProgressComponent
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class MonitoringDataManager(
    val project: Project?,
    open val settings: IntervalUpdateSettings,
    private val driverProvider: () -> MonitoringDriver
) : Disposable {
    abstract val connectionData: ConnectionData

    val driver: MonitoringDriver
        get() = driverProvider()

    @Suppress("LeakingThis")
    val updater = BdtMonitoringUpdater(this).also {
        Disposer.register(this, it)
    }

    //Should be lazy because freeze in arbitrary Cluster Wizard test
    val progressComponent by lazy {
        MonitoringProgressComponent(updater)
    }

    abstract val client: MonitoringClient

    protected fun init() {
        Disposer.register(this, client)
        Disposer.register(this, updater)
    }

    fun connect(calledByUser: Boolean, testConnection: Boolean, prepareConnection: () -> Unit) = try {
        updater.stopAll()
        updater.checkConnectionOrRefresh(calledByUser, prepareConnection)
    } finally {
        if (!testConnection) {
            driver.coroutineScope.launch {
                updater.reloadAll(checkConnection = checkConnectionOnRefresh())
            }
        }
    }

    protected open fun checkConnectionOnRefresh() = false

    fun getConnectionStatus() = client.getConnectionStatus()

    fun getRealUrl() = client.getRealUri().removeSuffix("/")

    fun actionWrapperSuspend(body: suspend () -> Unit): Job = driver.safeExecutor.coroutineScope.launch {
        try {
            body()
        } catch (t: Throwable) {
            RfsNotificationUtils.showExceptionMessage(project, t)
        }
    }

    override fun dispose() {}
    open fun onSuccessfulConnect() {}
}