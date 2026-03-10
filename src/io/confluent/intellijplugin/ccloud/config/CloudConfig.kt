package io.confluent.intellijplugin.ccloud.config

/**
 * Configuration for Confluent Cloud API endpoints and settings.
 *
 * Architecture:
 * - Control Plane: Cluster discovery and management (https://api.confluent.cloud)
 * - Data Plane: Direct cluster operations (https://pkc-xxx or https://psrc-xxx)
 */
object CloudConfig {

    const val BASE_PATH = "confluent.cloud"

    /**
     * Confluent Cloud API rate limit in requests per second.
     * See: https://docs.confluent.io/cloud/current/quotas/overview.html
     */
    const val API_RATE_LIMIT = 5

    val CONTROL_PLANE_BASE_URL: String
        get() = System.getProperty("ccloud.control-plane.base-url")
            ?: "https://api.$BASE_PATH"

    /**
     * Control Plane API endpoints for resource discovery and management.
     * Uses control plane OAuth token.
     */
    object ControlPlane {
        const val ORG_LIST_URI = "/org/v2/organizations"
        const val ENV_LIST_URI = "/org/v2/environments"
        const val LKC_LIST_URI = "/cmk/v2/clusters?environment=%s"
        const val SR_LIST_URI = "/srcm/v3/clusters?environment=%s"
    }

    /**
     * Data Plane API endpoints for cluster-specific operations.
     * Uses data plane OAuth token.
     *
     * Base URLs are obtained from cluster http_endpoint:
     * - Kafka: pkc-xxxxx.region.provider.confluent.cloud
     * - Schema Registry: psrc-xxxxx.region.provider.confluent.cloud
     */
    object DataPlane {
        /**
         * Kafka REST API v3 endpoints.
         */
        object Kafka {
            /** List topics: GET /kafka/v3/clusters/{cluster_id}/topics */
            const val TOPICS_URI = "/kafka/v3/clusters/%s/topics"

            /** Get topic: GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name} */
            const val TOPIC_URI = "/kafka/v3/clusters/%s/topics/%s"

            /** List partitions: GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/partitions */
            const val PARTITIONS_URI = "/kafka/v3/clusters/%s/topics/%s/partitions"

            /** Get partition: GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/partitions/{partition_id} */
            const val PARTITION_URI = "/kafka/v3/clusters/%s/topics/%s/partitions/%s"

            /** Topic configs: GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/configs */
            const val TOPIC_CONFIGS_URI = "/kafka/v3/clusters/%s/topics/%s/configs"

            /** List consumer groups: GET /kafka/v3/clusters/{cluster_id}/consumer-groups */
            const val CONSUMER_GROUPS_URI = "/kafka/v3/clusters/%s/consumer-groups"

            /** Describe consumer group: GET /kafka/v3/clusters/{cluster_id}/consumer-groups/{group_id} */
            const val CONSUMER_GROUP_URI = "/kafka/v3/clusters/%s/consumer-groups/%s"

            /** Consume records with guaranteed progress per partition. */
            const val CCLOUD_SIMPLE_CONSUME_API_PATH =
                "/kafka/v3/clusters/%s/internal/topics/%s/partitions/-/records:consume_guarantee_progress?return_raw_base64_records=true"

            /** Consume records from a single partition (GET with query params). */
            const val CCLOUD_SINGLE_PARTITION_CONSUME_API_PATH =
                "/kafka/v3/clusters/%s/internal/topics/%s/partitions/%d/records"
        }

        /**
         * Schema Registry API v1 endpoints.
         */
        object SchemaRegistry {
            // Read operations
            /** List subjects: GET /subjects */
            const val SUBJECTS_URI = "/subjects"

            /** Get subject all versions: GET /subjects/{subject}/versions */
            const val SUBJECT_VERSIONS_URI = "/subjects/%s/versions"

            /** Get subject specific version: GET /subjects/{subject}/versions/{version} */
            const val SUBJECT_VERSION_URI = "/subjects/%s/versions/%s"

            /** Get schema by ID: GET /schemas/ids/{id} */
            const val SCHEMA_BY_ID_URI = "/schemas/ids/%s"

            /** Get schema by GUID: GET /schemas/guids/{guid} */
            const val SCHEMA_BY_GUID_URI = "/schemas/guids/%s"

            // Write operations
            /** Register schema: POST /subjects/{subject}/versions */
            const val REGISTER_SCHEMA_URI = "/subjects/%s/versions"

            /** Check if schema exists: POST /subjects/{subject} */
            const val CHECK_SCHEMA_URI = "/subjects/%s"

            /** Delete subject: DELETE /subjects/{subject}?permanent={bool} */
            const val DELETE_SUBJECT_URI = "/subjects/%s"

            /** Delete schema version: DELETE /subjects/{subject}/versions/{version}?permanent={bool} */
            const val DELETE_VERSION_URI = "/subjects/%s/versions/%s"

            /** Get compatibility level for subject: GET /config/{subject} */
            const val GET_SUBJECT_COMPATIBILITY_URI = "/config/%s"
        }
    }
}
