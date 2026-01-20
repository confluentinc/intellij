package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.model.TopicPresentable

/**
 * Adapter to convert TopicData (from REST API) to TopicPresentable (for UI).
 *
 * Note: Some fields in TopicPresentable require additional API calls or Kafka Admin Client
 * and are set to null in the basic conversion:
 * - replicas: Requires partition-level details
 * - inSyncReplicas: Requires partition-level details
 * - underReplicatedPartitions: Requires partition-level details
 * - noLeaders: Requires partition-level details
 * - messageCount: Requires partition offset queries
 *
 * These can be populated later with more detailed API calls if needed.
 */
fun TopicData.toPresentable(): TopicPresentable {
    return TopicPresentable(
        name = topicName,
        internal = isInternal,
        partitionList = emptyList(),  // Would require separate API call to get partition details
        replicas = null,  // Not available in basic list API
        partitions = partitionsCount,
        inSyncReplicas = null,  // Not available in basic list API
        replicationFactor = replicationFactor,
        underReplicatedPartitions = null,  // Not available in basic list API
        noLeaders = null,  // Not available in basic list API
        messageCount = null,  // Not available in basic list API
        isFavorite = false  // Can be enhanced later with user preferences
    )
}

/**
 * Convert a list of TopicData to TopicPresentables.
 */
fun List<TopicData>.toPresentable(): List<TopicPresentable> {
    return map { it.toPresentable() }
}
