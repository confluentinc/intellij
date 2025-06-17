package com.jetbrains.bigdatatools.kafka.aws.credentials.utils

import com.jetbrains.bigdatatools.common.connection.exception.BdtConfigurationException
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class S3MissingCredentialsException : BdtConfigurationException(KafkaMessagesBundle.message("connection.error.s3.access.key.is.not.found"))

class S3MissingSecretKeyException : BdtConfigurationException(KafkaMessagesBundle.message("connection.error.s3.secret.key.is.not.found"))