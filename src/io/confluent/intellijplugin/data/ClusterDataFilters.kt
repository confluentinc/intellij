package io.confluent.intellijplugin.data

import io.confluent.intellijplugin.model.ConsumerGroupPresentable
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo

/**
 * Pure utility functions for filtering, sorting, and paginating cluster data.
 * Used exclusively in [BaseClusterDataManager].
 */
object ClusterDataFilters {

    fun applyTopicFilters(
        topics: List<TopicPresentable>,
        showInternalTopics: Boolean,
        filterName: String?
    ): List<TopicPresentable> {
        return topics.filter { topic ->
            (showInternalTopics || !topic.internal) &&
                (filterName == null || topic.name.lowercase().contains(filterName.lowercase()))
        }
    }

    fun sortTopicsWithFavorites(
        topics: List<TopicPresentable>,
        pinnedTopics: Set<String>,
        showFavoriteOnly: Boolean
    ): List<TopicPresentable> {
        val topicsWithFavorites = topics.map { topic ->
            topic.copy(isFavorite = pinnedTopics.contains(topic.name))
        }

        val filteredTopics = if (showFavoriteOnly) {
            topicsWithFavorites.filter { it.isFavorite }
        } else {
            topicsWithFavorites
        }

        return filteredTopics.sortedWith(
            compareByDescending<TopicPresentable> { it.isFavorite }
                .thenBy { it.name.lowercase() }
        )
    }

    fun applyTopicLimit(
        topics: List<TopicPresentable>,
        limit: Int?
    ): Pair<List<TopicPresentable>, Boolean> {
        return if (limit != null && topics.size > limit) {
            topics.take(limit) to true
        } else {
            topics to false
        }
    }

    // TODO(human): Should this be case-insensitive like applyTopicFilters and applySchemaFilters?
    fun applyConsumerGroupFilters(
        groups: List<ConsumerGroupPresentable>,
        filterName: String?
    ): List<ConsumerGroupPresentable> {
        return groups.filter { group ->
            filterName == null || group.consumerGroup.contains(filterName)
        }
    }

    fun applyConsumerGroupLimit(
        groups: List<ConsumerGroupPresentable>,
        limit: Int?
    ): Pair<List<ConsumerGroupPresentable>, Boolean> {
        return if (limit != null && groups.size > limit) {
            groups.take(limit) to true
        } else {
            groups to false
        }
    }

    fun applySchemaFilters(
        schemas: List<KafkaSchemaInfo>,
        filterName: String?
    ): List<KafkaSchemaInfo> {
        return schemas.filter { schema ->
            filterName == null || schema.name.lowercase().contains(filterName.lowercase())
        }
    }

    fun sortSchemasWithFavorites(
        schemas: List<KafkaSchemaInfo>,
        pinnedSchemas: Set<String>,
        showFavoriteOnly: Boolean
    ): List<KafkaSchemaInfo> {
        val schemasWithFavorites = schemas.map { schema ->
            schema.copy(isFavorite = pinnedSchemas.contains(schema.name))
        }

        val filteredSchemas = if (showFavoriteOnly) {
            schemasWithFavorites.filter { it.isFavorite }
        } else {
            schemasWithFavorites
        }

        return filteredSchemas.sortedWith(
            compareByDescending<KafkaSchemaInfo> { it.isFavorite }
                .thenBy { it.name.lowercase() }
        )
    }
}
