package io.confluent.intellijplugin.aws.credentials.utils

import io.confluent.intellijplugin.core.connection.exception.BdtConfigurationException
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class S3MissingCredentialsException : BdtConfigurationException(KafkaMessagesBundle.message("connection.error.s3.access.key.is.not.found"))

class S3MissingSecretKeyException : BdtConfigurationException(KafkaMessagesBundle.message("connection.error.s3.secret.key.is.not.found"))