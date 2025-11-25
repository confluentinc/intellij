package io.confluent.intellijplugin.ccloud.model

/**
 * Represents a Confluent Cloud organization.
 */
data class CloudOrganization(
    val id: String,
    val displayName: String,
    val isCurrent: Boolean = false,
    val connectionId: String? = null
) {
    fun withConnectionId(connectionId: String): CloudOrganization {
        return copy(connectionId = connectionId)
    }
}

