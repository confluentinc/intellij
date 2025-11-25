package io.confluent.intellijplugin.ccloud.model

/**
 * Reference to another Confluent Cloud resource.
 */
data class CloudReference(
    val id: String,
    val name: String? = null
)

