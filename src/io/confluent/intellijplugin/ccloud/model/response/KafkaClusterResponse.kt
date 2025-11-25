package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.confluent.intellijplugin.ccloud.model.CloudKafkaCluster
import io.confluent.intellijplugin.ccloud.model.CloudProvider
import io.confluent.intellijplugin.ccloud.model.CloudReference

/**
 * Response DTO for Kafka cluster list item.
 */
@JsonClass(generateAdapter = true)
data class KafkaClusterResponse(
    @Json(name = "api_version") val apiVersion: String? = null,
    val kind: String? = null,
    val id: String,
    val metadata: Map<String, Any?>? = null,
    val spec: KafkaClusterSpec? = null,
    val status: KafkaClusterStatus? = null
) {
    fun toRepresentation(): CloudKafkaCluster {
        if (spec == null) {
            return CloudKafkaCluster(
                id = id,
                displayName = "",
                cloudProvider = CloudProvider.NONE,
                region = "",
                httpEndpoint = null,
                bootstrapEndpoint = null,
                organization = null,
                environment = null
            )
        }
        val orgRef = spec.environment?.resourceName?.let { parseOrganization(it) }
        return CloudKafkaCluster(
            id = id,
            displayName = spec.displayName ?: "",
            cloudProvider = CloudProvider.of(spec.cloud),
            region = spec.region ?: "",
            httpEndpoint = spec.httpEndpoint,
            bootstrapEndpoint = spec.bootstrapEndpoint,
            organization = orgRef,
            environment = spec.environment?.let { CloudReference(it.id, null) }
        )
    }

    private fun parseOrganization(crnString: String): CloudReference? {
        val orgPattern = Regex("organization=([^/]+)")
        val match = orgPattern.find(crnString)
        return match?.groupValues?.get(1)?.let { orgId ->
            CloudReference(orgId, null)
        }
    }
}

@JsonClass(generateAdapter = true)
data class KafkaClusterSpec(
    @Json(name = "display_name") val displayName: String? = null,
    val availability: String? = null,
    val cloud: String? = null,
    val region: String? = null,
    val config: Map<String, Any?>? = null,
    @Json(name = "kafka_bootstrap_endpoint") val bootstrapEndpoint: String? = null,
    @Json(name = "http_endpoint") val httpEndpoint: String? = null,
    @Json(name = "api_endpoint") val apiEndpoint: String? = null,
    val environment: EnvironmentReference? = null,
    val network: NetworkReference? = null,
    val byok: KafkaClusterByok? = null
)

@JsonClass(generateAdapter = true)
data class KafkaClusterStatus(
    val phase: String? = null,
    val cku: Int? = null
)

@JsonClass(generateAdapter = true)
data class EnvironmentReference(
    val id: String? = null,
    val environment: String? = null,
    val related: String? = null,
    @Json(name = "resource_name") val resourceName: String? = null
)

@JsonClass(generateAdapter = true)
data class NetworkReference(
    val id: String? = null,
    val environment: String? = null,
    val related: String? = null,
    @Json(name = "resource_name") val resourceName: String? = null
)

@JsonClass(generateAdapter = true)
data class KafkaClusterByok(
    val id: String? = null,
    val related: String? = null,
    @Json(name = "resource_name") val resourceName: String? = null
)


