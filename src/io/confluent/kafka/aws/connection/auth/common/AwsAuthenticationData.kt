package io.confluent.kafka.aws.connection.auth.common

import java.io.Serializable

abstract class AwsAuthenticationData : Serializable {
  abstract val authType: String
  abstract fun getCredentialsProvider(): BdtAwsCredentialsProvider
}