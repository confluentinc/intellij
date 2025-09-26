package io.confluent.intellijplugin.core.connection.exception

import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

open class BdtConnectionException(open val shortDescription: @Nls String = KafkaMessagesBundle.message("connection.error.exception"),
                                  cause: Throwable? = null) : RuntimeException(cause) {
  override val message: String? get() = shortDescription

  open val additionalNotification: String = ""
  open val autoReconnect: Boolean = false
}

/**
 * For non-specific problems like network issues, totally incorrect responses, problems with creating request etc.
 * These exceptions cannot be replaced with user-friendly suggestions
 * because some information from the exception might be useful for investigation the problem with configuration.
 * Some cases of these exceptions can be then caught and replaced by subclasses of [BdtSpecificConnectionException]
 * if parameters or place of exception allow to draw unambiguous conclusion.
 */
abstract class BdtGenericConnectionException : BdtConnectionException() {
  override val autoReconnect: Boolean get() = true
  override val message: String? get() = cause?.message ?: shortDescription
}

class BdtUnexpectedConnectionException(
  override val cause: Throwable?,
  override val shortDescription: String = KafkaMessagesBundle.message("connection.error.common.unexpected.exception")
) : BdtGenericConnectionException()

/**
 * For specific problems like missing authentication or easy-to-fix misconfigurations,
 * kinda 'Everything is ok but...'
 * These exceptions should be transformed into user-friendly suggestions.
 */
abstract class BdtSpecificConnectionException(cause: Throwable? = null) : BdtConnectionException(cause = cause) {
  override val autoReconnect: Boolean get() = false
  override val message: String? get() = shortDescription
}

class BdtDriverNotInitializedException : BdtSpecificConnectionException() {
  override val shortDescription: @Nls String
    get() = KafkaMessagesBundle.message("connection.error.driver.not.initialized")
}

open class BdtConfigurationException(
  override val shortDescription: String,
  override val cause: Throwable? = null,
) : BdtConnectionException() {
  override val autoReconnect: Boolean get() = false
  override val message: String? get() = shortDescription
}

class SshConfigNotFoundException(val configId: String) : BdtConfigurationException("ssh config not found: $configId")

abstract class BdtAuthenticationException : BdtSpecificConnectionException()

class BdtHostUnavailableException(val url: String, cause: Throwable? = null) : BdtSpecificConnectionException(cause) {
  override val shortDescription: String
    get() = KafkaMessagesBundle.message("connection.error.common.host.unavailabe", url)
}

class BdtKerberosException(cause: Throwable?,
                           override val shortDescription: String = cause?.message ?: "Kerberos Error",
                           override val additionalNotification: String) : BdtSpecificConnectionException(cause)
