package com.jetbrains.bigdatatools.kafka.aws.connection.auth.defaultauth

import com.jetbrains.bigdatatools.kafka.aws.connection.auth.AuthenticationType
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.common.AwsAuthenticationData

class DefaultAwsAuthenticationData : AwsAuthenticationData() {
  override val authType: String = AuthenticationType.DEFAULT.id

  override fun getCredentialsProvider() = DefaultBdtAwsCredentialsProvider()
}