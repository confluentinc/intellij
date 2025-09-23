package io.confluent.kafka.aws.connection.auth.defaultauth

import io.confluent.kafka.aws.connection.auth.AuthenticationType
import io.confluent.kafka.aws.connection.auth.common.AwsAuthenticationData

class DefaultAwsAuthenticationData : AwsAuthenticationData() {
  override val authType: String = AuthenticationType.DEFAULT.id

  override fun getCredentialsProvider() = DefaultBdtAwsCredentialsProvider()
}