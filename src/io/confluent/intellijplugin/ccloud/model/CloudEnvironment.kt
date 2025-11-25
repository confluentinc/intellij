package io.confluent.intellijplugin.ccloud.model

/**
 * Represents a Confluent Cloud environment.
 */
data class CloudEnvironment(
    val id: String,
    val displayName: String,
    val organization: CloudReference? = null,
    val governancePackage: CloudGovernancePackage = CloudGovernancePackage.NONE,
    val connectionId: String? = null
) {
    fun withConnectionId(connectionId: String): CloudEnvironment {
        return copy(connectionId = connectionId)
    }

    fun withOrganization(org: CloudOrganization?): CloudEnvironment {
        return copy(organization = org?.let { CloudReference(it.id, it.displayName) })
    }
}

