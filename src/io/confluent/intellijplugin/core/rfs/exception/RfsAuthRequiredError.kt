package io.confluent.intellijplugin.core.rfs.exception

import io.confluent.intellijplugin.core.connection.exception.BdtAuthenticationException

class RfsAuthRequiredError(val msg: String = "Auth required", val source: Throwable? = null) : BdtAuthenticationException() {
  override val message get() = msg
  override val cause get() = source
}