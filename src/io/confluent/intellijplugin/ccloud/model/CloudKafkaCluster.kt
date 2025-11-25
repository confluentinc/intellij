package io.confluent.intellijplugin.ccloud.model

/**
 * Represents a Confluent Cloud Kafka cluster
 */
data class CloudKafkaCluster(
    val id: String,
    val displayName: String,
    val cloudProvider: CloudProvider,
    val region: String,
    val httpEndpoint: String? = null,
    val bootstrapEndpoint: String? = null,
    val organization: CloudReference? = null,
    val environment: CloudReference? = null,
    val connectionId: String? = null
) {
    fun withConnectionId(connectionId: String): CloudKafkaCluster {
        return copy(connectionId = connectionId)
    }

    fun withEnvironment(env: CloudEnvironment): CloudKafkaCluster {
        return copy(environment = CloudReference(env.id, env.displayName))
    }

    fun withOrganization(org: CloudOrganization?): CloudKafkaCluster {
        return copy(organization = org?.let { CloudReference(it.id, it.displayName) })
    }

    fun matches(criteria: CloudSearchCriteria): Boolean {
        if (criteria.resourceId != null && id != criteria.resourceId) return false
        if (criteria.nameContaining != null && !displayName.contains(criteria.nameContaining, ignoreCase = true)) return false
        if (criteria.environmentIdContaining != null && environment?.id?.contains(criteria.environmentIdContaining, ignoreCase = true) != true) return false
        if (criteria.cloudProviderContaining != null && cloudProvider.name.contains(criteria.cloudProviderContaining, ignoreCase = true) != true) return false
        if (criteria.regionContaining != null && !region.contains(criteria.regionContaining, ignoreCase = true)) return false
        return true
    }
}

