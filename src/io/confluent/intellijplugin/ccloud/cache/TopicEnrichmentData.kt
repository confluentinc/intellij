package io.confluent.intellijplugin.ccloud.cache

/**
 * Enrichment data for topics (requires additional API calls beyond basic topic list).
 */
data class TopicEnrichmentData(
    /** Total message count across all partitions (null if unavailable). */
    val messageCount: Long? = null,
    /** Number of in-sync replicas (null if unavailable). */
    val inSyncReplicas: Int? = null
)
