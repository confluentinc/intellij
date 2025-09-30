package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.util.KafkaMessagesBundle

/**
 * User: Dmitry.Naydanov
 * Date: 2019-05-27.
 */
class ModificationKey(@NlsContexts.Label val label: String)

interface ConnectionSettingsListener {
  fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {}
  fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {}
  fun onConnectionModified(project: Project?, connectionData: ConnectionData, modified: Collection<ModificationKey>) {}

  /** ID could be used in ConnectionSettingsService.removeListener(id). */
  fun getId(): String = this.javaClass.typeName
}

object CommonSettingsKeys {
  val OPERATIONS_TIMEOUT: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.operation.timeouts"))

  val NAME_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.name"))
  val URL_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.url"))
  val PORT_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.port"))
  val ROOT_PATH_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.rootPath"))

  val COOKIE_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.cookie"))
  val LOGIN_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.login"))
  val PASS_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.password"))

  val ANONYMOUS_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.anonymous"))
  val IS_GLOBAL_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.perProject"))

  val ENABLED_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.enabled"))

  // Basic auth.
  val BASIC_AUTH_LOGIN_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.login"))
  val BASIC_AUTH_PASSWORD_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.password"))
  val ENABLE_BASIC_AUTH_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.basicAuth.enabled"))

  // Proxy Settings.
  val PROXY_ENABLE_TYPE_KEY: ModificationKey = ModificationKey("")
  val PROXY_TYPE_KEY: ModificationKey = ModificationKey("")
  val PROXY_HOST_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.host"))
  val PROXY_PORT_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.port"))
  val ENABLE_PROXY_AUTH_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.proxyAuth.enabled"))
  val PROXY_LOGIN_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.login"))
  val PROXY_PASSWORD_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.password"))
}