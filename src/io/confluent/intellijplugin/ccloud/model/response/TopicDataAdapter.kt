package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.cache.TopicEnrichmentData
import io.confluent.intellijplugin.model.TopicPresentable

/**
 * Converts TopicData (from REST API) to TopicPresentable (for UI).
 * Some fields require additional API calls and can be provided via enrichment data.
 */
fun TopicData.toPresentable(enrichmentData: TopicEnrichmentData? = null): TopicPresentable {
    return TopicPresentable(
        name = topicName,
        internal = isInternal,
        partitionList = emptyList(),
        partitions = partitionsCount,
        replicationFactor = replicationFactor,
        underReplicatedPartitions = null,
        noLeaders = null,
        messageCount = enrichmentData?.messageCount,
        isFavorite = false
    )
}

/** Converts list of TopicData to TopicPresentables without enrichment. */
fun List<TopicData>.toPresentable(): List<TopicPresentable> {
    return map { it.toPresentable() }
}

/** Converts list of TopicData to TopicPresentables with enrichment data. */
fun List<TopicData>.toPresentableWithEnrichment(enrichmentMap: Map<String, TopicEnrichmentData>): List<TopicPresentable> {
    return map { topicData ->
        topicData.toPresentable(enrichmentMap[topicData.topicName])
    }
}
