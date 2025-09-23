package io.confluent.kafka.core.rfs.exception

import io.confluent.kafka.core.connection.exception.BdtAuthenticationException

class RfsAuthRequiredError(val msg: String = "Auth required", val source: Throwable? = null) : BdtAuthenticationException() {
  override val message get() = msg
  override val cause get() = source
}