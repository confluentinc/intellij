package io.confluent.kafka.core.monitoring.data.model

data class DataModelError(override val message: String, override val cause: Throwable?) : Exception()