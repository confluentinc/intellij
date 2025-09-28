package io.confluent.intellijplugin.aws.connection.auth.defaultauth

import io.confluent.intellijplugin.aws.connection.auth.common.BdtAwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

class DefaultBdtAwsCredentialsProvider : BdtAwsCredentialsProvider {
  override fun getCredentials(): DefaultCredentialsProvider = DefaultCredentialsProvider.builder().build()
}