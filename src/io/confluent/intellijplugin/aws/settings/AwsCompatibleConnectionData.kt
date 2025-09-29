package io.confluent.intellijplugin.aws.settings

import io.confluent.intellijplugin.aws.connection.auth.AuthenticationType
import io.confluent.intellijplugin.core.connection.ProxyEnableType
import io.confluent.intellijplugin.core.rfs.settings.RemoteFsDriverProvider
import io.confluent.intellijplugin.core.settings.connections.CredentialId

interface RegionAwareConnectionData {
  var region: String
}

abstract class AwsCompatibleConnectionData(
  initialActiveAuthenticationType: String = AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE.id,
  connectionName: String
) : RemoteFsDriverProvider(connectionName), RegionAwareConnectionData {
  var version: Int? = null

  var activeAuthenticationType: String = initialActiveAuthenticationType
    set(value) {
      field = AuthenticationType.migrateDeprecated(value)
    }
  var proxyEnableType: ProxyEnableType = ProxyEnableType.DISABLED
  var proxyHost: String = ""
  var proxyPort: Int = 80
  var proxyAuthEnabled: Boolean = false

  override fun credentialIds(): List<CredentialId> = super.credentialIds() + SECRET_KEY_ID + PROXY_CREDENTIALS_ID

  companion object {
    val PROXY_CREDENTIALS_ID: CredentialId = CredentialId("proxy.auth.credentials")
    val SECRET_KEY_ID: CredentialId = CredentialId("S3 basic credentials secret key")
    const val DEFAULT_PROFILE_NAME: String = "default"
  }
}


