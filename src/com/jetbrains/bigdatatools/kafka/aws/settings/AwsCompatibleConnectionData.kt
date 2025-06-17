package com.jetbrains.bigdatatools.kafka.aws.settings

import com.jetbrains.bigdatatools.common.connection.ProxyEnableType
import com.jetbrains.bigdatatools.common.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.common.settings.connections.CredentialId
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.AuthenticationType

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
  var proxyEnableType = ProxyEnableType.DISABLED
  var proxyHost: String = ""
  var proxyPort: Int = 80
  var proxyAuthEnabled = false

  override fun credentialIds() = super.credentialIds() + SECRET_KEY_ID + PROXY_CREDENTIALS_ID

  companion object {
    val PROXY_CREDENTIALS_ID = CredentialId("proxy.auth.credentials")
    val SECRET_KEY_ID = CredentialId("S3 basic credentials secret key")
    const val DEFAULT_PROFILE_NAME = "default"
  }
}


