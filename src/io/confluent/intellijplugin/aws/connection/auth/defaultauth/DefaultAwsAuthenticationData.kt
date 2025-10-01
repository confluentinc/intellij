package io.confluent.intellijplugin.aws.connection.auth.defaultauth

import io.confluent.intellijplugin.aws.connection.auth.AuthenticationType
import io.confluent.intellijplugin.aws.connection.auth.common.AwsAuthenticationData

class DefaultAwsAuthenticationData : AwsAuthenticationData() {
  override val authType: String = AuthenticationType.DEFAULT.id

  override fun getCredentialsProvider() = DefaultBdtAwsCredentialsProvider()
}