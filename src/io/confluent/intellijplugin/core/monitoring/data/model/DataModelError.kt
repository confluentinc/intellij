package io.confluent.intellijplugin.core.monitoring.data.model

data class DataModelError(override val message: String, override val cause: Throwable?) : Exception()