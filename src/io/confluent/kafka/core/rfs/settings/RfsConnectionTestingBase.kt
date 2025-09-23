package io.confluent.kafka.core.rfs.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.core.rfs.driver.ActivitySource
import io.confluent.kafka.core.rfs.driver.ConnectedConnectionStatus
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.FailedConnectionStatus
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.settings.connections.ConnectionTesting
import io.confluent.kafka.core.settings.connections.ConnectionTestingSession
import io.confluent.kafka.core.settings.defaultui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class RfsConnectionTestingBase<D : ConnectionData>(protected val project: Project,
                                                        private val settingsCustomizer: SettingsPanelCustomizer<D>?) : ConnectionTesting<D> {

  private suspend fun ConnectionTestingSession<D>.updateUiOnTestConnectionFinish(connectionStatus: ConnectionStatus) {
    withContext(Dispatchers.EDT) {
      settingsCustomizer?.onTestConnectionFinish(driver, connectionStatus is ConnectionSuccessful)
    }
    updateStatusIndicator(connectionStatus)
  }

  final override suspend fun ConnectionTestingSession<D>.validateAndTest() {
    withContext(Dispatchers.EDT) {
      settingsCustomizer?.onTestConnectionStart()
    }


    val validationErrors = settingsCustomizer?.getValidationErrors() ?: emptyList()
    val connectionStatus = if (validationErrors.isNotEmpty()) {
      connectionError(validationErrors)
    }
    else {
      runCatching {
        checkConnection()
      }.getOrElse {
        ConnectionError(it)
      }
    }
    updateUiOnTestConnectionFinish(connectionStatus)
  }

  open suspend fun ConnectionTestingSession<D>.checkConnection(): ConnectionStatus {
    val driver = testConnectionData.createDriver(project, isTest = true)
    Disposer.register(testDisposable, driver)
    this.driver = driver
    return checkDriver(driver)
  }

  protected suspend fun checkDriver(driver: Driver): ConnectionStatus {
    return when (val status = driver.refreshConnection(ActivitySource.TEST_ACTION)) {
      is FailedConnectionStatus -> ConnectionError(status.getException())
      is ConnectedConnectionStatus -> ConnectionSuccessful()
    }
  }
}