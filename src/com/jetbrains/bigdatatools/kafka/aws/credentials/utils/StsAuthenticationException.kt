package com.jetbrains.bigdatatools.kafka.aws.credentials.utils

import com.jetbrains.bigdatatools.common.connection.exception.BdtAuthenticationException
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.awscore.exception.AwsServiceException

class StsAuthenticationException(override val cause: AwsServiceException) : BdtAuthenticationException() {
  override val shortDescription: String = KafkaMessagesBundle.message("connection.error.aws.sts.expired")
  override val message = cause.message ?: shortDescription
}

