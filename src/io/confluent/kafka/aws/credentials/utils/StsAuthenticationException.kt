package io.confluent.kafka.aws.credentials.utils

import io.confluent.kafka.core.connection.exception.BdtAuthenticationException
import io.confluent.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.awscore.exception.AwsServiceException

class StsAuthenticationException(override val cause: AwsServiceException) : BdtAuthenticationException() {
  override val shortDescription: String = KafkaMessagesBundle.message("connection.error.aws.sts.expired")
  override val message: String = cause.message ?: shortDescription
}

