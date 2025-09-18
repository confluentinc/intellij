package com.jetbrains.bigdatatools.kafka.aws.connection.auth.defaultauth

import com.jetbrains.bigdatatools.kafka.aws.connection.auth.common.BdtAwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

class DefaultBdtAwsCredentialsProvider : BdtAwsCredentialsProvider {
  override fun getCredentials(): DefaultCredentialsProvider = DefaultCredentialsProvider.builder().build()
}