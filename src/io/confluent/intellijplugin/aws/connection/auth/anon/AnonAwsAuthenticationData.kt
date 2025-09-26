package io.confluent.intellijplugin.aws.connection.auth.anon

import io.confluent.intellijplugin.aws.connection.auth.AuthenticationType
import io.confluent.intellijplugin.aws.connection.auth.common.AwsAuthenticationData
import io.confluent.intellijplugin.aws.connection.auth.common.BdtAwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider

class AnonAwsAuthenticationData : AwsAuthenticationData() {
  override val authType: String = AuthenticationType.DEFAULT.id

  override fun getCredentialsProvider() = object : BdtAwsCredentialsProvider {
    override fun getCredentials() = AnonymousCredentialsProvider.create()
  }
}