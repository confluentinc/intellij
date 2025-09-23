package io.confluent.kafka.aws.connection.auth.common

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

interface BdtAwsCredentialsProvider {
  fun getCredentials(): AwsCredentialsProvider
}