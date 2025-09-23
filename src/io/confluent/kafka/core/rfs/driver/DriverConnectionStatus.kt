package io.confluent.kafka.core.rfs.driver

import io.confluent.kafka.core.connection.exception.BdtConnectionException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class DriverConnectionStatus {
  abstract fun getException(): Throwable?
  fun isConnected() = this is ConnectedConnectionStatus
  fun isConnecting() = this is ConnectingConnectionStatus
  @OptIn(ExperimentalContracts::class)
  fun isFailed(): Boolean {
    contract {
      returns(true) implies (this@DriverConnectionStatus is FailedConnectionStatus)
    }
    return this is FailedConnectionStatus
  }
  fun shouldReconnect() = this is FailedConnectionStatus && this.autoReconnect
  abstract val name: String
}

sealed class ReadyConnectionStatus : DriverConnectionStatus()

class FailedConnectionStatus(private val exception: Throwable, val autoReconnect: Boolean) : ReadyConnectionStatus() {
  constructor(exception: Throwable) : this(exception, (exception as? BdtConnectionException)?.autoReconnect ?: false)

  override fun getException(): Throwable = exception
  override val name = "FAILED"

  override fun toString(): String {
    return "FAILED, exception: ${exception}"
  }
}

object ConnectingConnectionStatus : DriverConnectionStatus() {
  override fun getException(): Throwable? = null
  override val name = "CONNECTING"
}

object ConnectedConnectionStatus : ReadyConnectionStatus() {
  override fun getException(): Throwable? = null
  override val name = "CONNECTED"
}