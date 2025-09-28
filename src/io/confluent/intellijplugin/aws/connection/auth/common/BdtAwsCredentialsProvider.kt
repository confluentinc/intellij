package io.confluent.intellijplugin.aws.connection.auth.common

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

interface BdtAwsCredentialsProvider {
  fun getCredentials(): AwsCredentialsProvider
}