package io.confluent.intellijplugin.core.connection

import com.intellij.credentialStore.Credentials
import io.confluent.intellijplugin.util.KafkaMessagesBundle

data class ConnectionConfig(
  val url: String,
  val connectionGroupId: String,
  val basicAuthCredentials: Credentials? = null,
  val proxy: ProxySettings? = null,
  //val cookieStore: CookieStore? = null,
  //val redirectExcludes: List<RedirectExclude> = listOf(OauthRedirectExclude),
  val headers: Map<String, String>
)

enum class ProxyEnableType(val title: String) {
  DISABLED(KafkaMessagesBundle.message("settings.proxy.disabled")),
  GLOBAL(KafkaMessagesBundle.message("settings.proxy.idea")),
  CUSTOM(KafkaMessagesBundle.message("settings.proxy.manual"))
}

enum class ProxyType {
  HTTP, SOCKS
}

data class ProxySettings(
  val type: ProxyType,
  val host: String,
  val port: Int,
  val credentials: Credentials?,
  val ignoreHosts: String? = null)
