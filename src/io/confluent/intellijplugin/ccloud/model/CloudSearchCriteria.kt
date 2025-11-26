package io.confluent.intellijplugin.ccloud.model

/**
 * Search criteria for filtering Confluent Cloud resources.
 */
data class CloudSearchCriteria(
    val resourceId: String? = null,
    val nameContaining: String? = null,
    val environmentIdContaining: String? = null,
    val cloudProviderContaining: String? = null,
    val regionContaining: String? = null
) {
    companion object {
        fun create(): Builder = Builder()

        class Builder {
            private var resourceId: String? = null
            private var nameContaining: String? = null
            private var environmentIdContaining: String? = null
            private var cloudProviderContaining: String? = null
            private var regionContaining: String? = null

            fun withResourceId(id: String) = apply { this.resourceId = id }
            fun withNameContaining(name: String) = apply { this.nameContaining = name }
            fun withEnvironmentIdContaining(envId: String) = apply { this.environmentIdContaining = envId }
            fun withCloudProviderContaining(provider: String) = apply { this.cloudProviderContaining = provider }
            fun withRegionContaining(region: String) = apply { this.regionContaining = region }

            fun build() = CloudSearchCriteria(
                resourceId = resourceId,
                nameContaining = nameContaining,
                environmentIdContaining = environmentIdContaining,
                cloudProviderContaining = cloudProviderContaining,
                regionContaining = regionContaining
            )
        }
    }
}

