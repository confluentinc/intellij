package io.confluent.intellijplugin.ccloud.model

/**
 * Represents a Confluent Cloud Schema Registry.
 */
data class CloudSchemaRegistry(
    val id: String,
    val httpEndpoint: String,
    val cloudProvider: CloudProvider,
    val region: String,
    val organization: CloudReference? = null,
    val environment: CloudReference? = null,
    val connectionId: String? = null
) {
    fun withConnectionId(connectionId: String): CloudSchemaRegistry {
        return copy(connectionId = connectionId)
    }

    fun withEnvironment(env: CloudEnvironment): CloudSchemaRegistry {
        return copy(environment = CloudReference(env.id, env.displayName))
    }

    fun withOrganization(org: CloudOrganization?): CloudSchemaRegistry {
        return copy(organization = org?.let { CloudReference(it.id, it.displayName) })
    }
}

