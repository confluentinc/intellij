package io.confluent.intellijplugin.aws.credentials.utils

import io.confluent.intellijplugin.core.connection.exception.BdtAuthenticationException
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import software.amazon.awssdk.awscore.exception.AwsServiceException

class StsAuthenticationException(override val cause: AwsServiceException) : BdtAuthenticationException() {
    override val shortDescription: String = KafkaMessagesBundle.message("connection.error.aws.sts.expired")
    override val message: String = cause.message ?: shortDescription
}

