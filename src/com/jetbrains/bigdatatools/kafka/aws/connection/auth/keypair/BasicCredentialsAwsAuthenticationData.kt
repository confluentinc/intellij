package io.confluent.kafka.aws.connection.auth.keypair

import io.confluent.kafka.aws.connection.auth.AuthenticationType
import io.confluent.kafka.aws.connection.auth.common.AwsAuthenticationData
import io.confluent.kafka.aws.ui.external.AwsSettingsInfo

class BasicCredentialsAwsAuthenticationData(private val awsSettingsInfo: AwsSettingsInfo) : AwsAuthenticationData() {
  override val authType: String = AuthenticationType.KEY_PAIR.id

  override fun getCredentialsProvider() = FromCredAttributesBdtAwsCredentialsProvider(awsSettingsInfo)
}