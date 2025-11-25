package io.confluent.intellijplugin.ccloud.exception

/**
 * Exception thrown when fetching Confluent Cloud resources fails.
 */
class CloudResourceFetchingException(message: String, cause: Throwable? = null) : Exception(message, cause)

