package io.confluent.intellijplugin.core.monitoring.connection

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.connection.exception.BdtDriverNotInitializedException
import io.confluent.intellijplugin.core.rfs.driver.ConnectedConnectionStatus
import io.confluent.intellijplugin.core.rfs.driver.ConnectingConnectionStatus
import io.confluent.intellijplugin.core.rfs.driver.DriverConnectionStatus
import io.confluent.intellijplugin.core.rfs.driver.FailedConnectionStatus
import io.confluent.intellijplugin.core.util.SmartLogger

abstract class MonitoringClient(val project: Project?) : Disposable {
    private var state: DriverConnectionStatus? = null

    fun connect(calledByUser: Boolean, prepareConnection: () -> Unit = {}) {
        try {
            state = ConnectingConnectionStatus

            prepareConnection()
            connectInner(calledByUser)
            checkConnectionInner()

            logger.info("Successfully connected to ${getRealUri()}")
            state = ConnectedConnectionStatus
        } catch (t: Throwable) {
            logger.info("Connection error to ${getRealUri()}", t)
            state = FailedConnectionStatus(t)
            throw t
        }
    }

    fun checkConnection() = try {
        checkConnectionInner()
        state = ConnectedConnectionStatus
    } catch (t: Throwable) {
        state = FailedConnectionStatus(t)
        throw t
    }

    fun getConnectionStatus(): DriverConnectionStatus =
        state ?: FailedConnectionStatus(BdtDriverNotInitializedException())

    val connectionError: Throwable? get() = state?.getException()
    fun isConnecting() = state == ConnectingConnectionStatus
    fun isConnected() = state == ConnectedConnectionStatus
    fun isInited() = state == ConnectedConnectionStatus || state is FailedConnectionStatus

    abstract fun getRealUri(): String
    protected abstract fun checkConnectionInner()
    protected abstract fun connectInner(calledByUser: Boolean)

    companion object {
        val logger = SmartLogger(this::class.java)
    }
}

