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
        }

        /**
         * Schema Registry API v1 endpoints.
         */
        object SchemaRegistry {
            /** List subjects: GET /subjects */
            const val SUBJECTS_URI = "/subjects"

            /** Get subject versions: GET /subjects/{subject}/versions */
            const val SUBJECT_VERSIONS_URI = "/subjects/%s/versions"

            /** Get subject version: GET /subjects/{subject}/versions/{version} */
            const val SUBJECT_VERSION_URI = "/subjects/%s/versions/%s"

            /** Get schema by ID: GET /schemas/ids/{id} */
            const val SCHEMA_BY_ID_URI = "/schemas/ids/%s"

            /** Delete subject: DELETE /subjects/{subject} */
            const val DELETE_SUBJECT_URI = "/subjects/%s"

            /** Schema Registry config: GET /config */
            const val CONFIG_URI = "/config"

            /** Subject config: GET /config/{subject} */
            const val SUBJECT_CONFIG_URI = "/config/%s"
        }
    }
}

