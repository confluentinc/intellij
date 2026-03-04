package io.confluent.intellijplugin.data

import io.confluent.intellijplugin.model.ConsumerGroupPresentable
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import org.apache.kafka.common.GroupState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ClusterDataFiltersTest {

    private fun topic(name: String, internal: Boolean = false) =
        TopicPresentable(name = name, internal = internal)

    private fun group(name: String) =
        ConsumerGroupPresentable(state = GroupState.STABLE, consumerGroup = name)

    private fun schema(name: String) =
        KafkaSchemaInfo(name = name)

    @Nested
    @DisplayName("applyTopicFilters")
    inner class ApplyTopicFilters {

        @Test
        fun `should keep user topics and filter out internal topics when showInternalTopics is false`() {
            val topics = listOf(
                topic("orders"),
                topic("__consumer_offsets", internal = true),
                topic("payments")
            )

            val result = ClusterDataFilters.applyTopicFilters(topics, showInternalTopics = false, filterName = null)

            assertEquals(listOf("orders", "payments"), result.map { it.name })
        }

        @Test
        fun `should keep all topics including internal when showInternalTopics is true`() {
            val topics = listOf(
                topic("orders"),
                topic("__consumer_offsets", internal = true),
                topic("payments")
            )

            val result = ClusterDataFilters.applyTopicFilters(topics, showInternalTopics = true, filterName = null)

            assertEquals(3, result.size)
            assertTrue(result.any { it.internal })
        }

        @Test
        fun `should filter by name case-insensitively`() {
            val topics = listOf(
                topic("Payments"),
                topic("orders"),
                topic("pay-history")
            )

            val result = ClusterDataFilters.applyTopicFilters(topics, showInternalTopics = true, filterName = "pay")

            assertEquals(listOf("Payments", "pay-history"), result.map { it.name })
        }

        @Test
        fun `should combine internal filter and name filter`() {
            val topics = listOf(
                topic("orders"),
                topic("__consumer_offsets", internal = true),
                topic("order-events"),
                topic("__transaction_state", internal = true)
            )

            val result = ClusterDataFilters.applyTopicFilters(topics, showInternalTopics = false, filterName = "order")

            assertEquals(listOf("orders", "order-events"), result.map { it.name })
        }

        @Test
        fun `should return all non-internal topics when filter is null`() {
            val topics = listOf(topic("a"), topic("b"), topic("__internal", internal = true))

            val result = ClusterDataFilters.applyTopicFilters(topics, showInternalTopics = false, filterName = null)

            assertEquals(listOf("a", "b"), result.map { it.name })
        }

        @Test
        fun `should return empty list when input is empty`() {
            val result = ClusterDataFilters.applyTopicFilters(emptyList(), showInternalTopics = false, filterName = "x")

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("sortTopicsWithFavorites")
    inner class SortTopicsWithFavorites {

        @Test
        fun `should sort pinned topics before non-pinned then alphabetically within each group`() {
            val topics = listOf(topic("zebra"), topic("alpha"), topic("mango"))

            val result = ClusterDataFilters.sortTopicsWithFavorites(topics, pinnedTopics = setOf("mango"), showFavoriteOnly = false)

            assertEquals(listOf("mango", "alpha", "zebra"), result.map { it.name })
            assertTrue(result[0].isFavorite)
            assertFalse(result[1].isFavorite)
        }

        @Test
        fun `should return only pinned topics when showFavoriteOnly is true`() {
            val topics = listOf(topic("a"), topic("b"), topic("c"))

            val result = ClusterDataFilters.sortTopicsWithFavorites(topics, pinnedTopics = setOf("b"), showFavoriteOnly = true)

            assertEquals(listOf("b"), result.map { it.name })
            assertTrue(result.all { it.isFavorite })
        }

        @Test
        fun `should sort all topics alphabetically when no topics are pinned`() {
            val topics = listOf(topic("cherry"), topic("apple"), topic("banana"))

            val result = ClusterDataFilters.sortTopicsWithFavorites(topics, pinnedTopics = emptySet(), showFavoriteOnly = false)

            assertEquals(listOf("apple", "banana", "cherry"), result.map { it.name })
            assertTrue(result.none { it.isFavorite })
        }

        @Test
        fun `should mark all as favorite and sort alphabetically when all are pinned`() {
            val topics = listOf(topic("c"), topic("a"), topic("b"))

            val result = ClusterDataFilters.sortTopicsWithFavorites(topics, pinnedTopics = setOf("a", "b", "c"), showFavoriteOnly = false)

            assertEquals(listOf("a", "b", "c"), result.map { it.name })
            assertTrue(result.all { it.isFavorite })
        }

        @Test
        fun `should silently ignore pinned topic names that do not exist in the list`() {
            val topics = listOf(topic("x"), topic("y"))

            val result = ClusterDataFilters.sortTopicsWithFavorites(topics, pinnedTopics = setOf("nonexistent"), showFavoriteOnly = false)

            assertEquals(listOf("x", "y"), result.map { it.name })
            assertTrue(result.none { it.isFavorite })
        }
    }

    @Nested
    @DisplayName("applyTopicLimit")
    inner class ApplyTopicLimit {

        @Test
        fun `should return all topics with hasMore false when under limit`() {
            val topics = listOf(topic("a"), topic("b"))

            val (result, hasMore) = ClusterDataFilters.applyTopicLimit(topics, limit = 5)

            assertEquals(2, result.size)
            assertFalse(hasMore)
        }

        @Test
        fun `should truncate and set hasMore true when over limit`() {
            val topics = listOf(topic("a"), topic("b"), topic("c"), topic("d"))

            val (result, hasMore) = ClusterDataFilters.applyTopicLimit(topics, limit = 2)

            assertEquals(listOf("a", "b"), result.map { it.name })
            assertTrue(hasMore)
        }

        @Test
        fun `should return all topics with hasMore false when limit is null`() {
            val topics = listOf(topic("a"), topic("b"), topic("c"))

            val (result, hasMore) = ClusterDataFilters.applyTopicLimit(topics, limit = null)

            assertEquals(3, result.size)
            assertFalse(hasMore)
        }

        @Test
        fun `should not truncate when limit exactly equals list size`() {
            val topics = listOf(topic("a"), topic("b"), topic("c"))

            val (result, hasMore) = ClusterDataFilters.applyTopicLimit(topics, limit = 3)

            assertEquals(3, result.size)
            assertFalse(hasMore)
        }
    }

    @Nested
    @DisplayName("applyConsumerGroupFilters")
    inner class ApplyConsumerGroupFilters {

        @Test
        fun `should filter groups by substring match`() {
            val groups = listOf(group("my-app-group"), group("other-group"), group("my-app-2"))

            val result = ClusterDataFilters.applyConsumerGroupFilters(groups, filterName = "my-app")

            assertEquals(listOf("my-app-group", "my-app-2"), result.map { it.consumerGroup })
        }

        @Test
        fun `should return all groups when filter is null`() {
            val groups = listOf(group("a"), group("b"))

            val result = ClusterDataFilters.applyConsumerGroupFilters(groups, filterName = null)

            assertEquals(2, result.size)
        }

        @Test
        fun `should return empty when filter matches no groups`() {
            val groups = listOf(group("alpha"), group("beta"))

            val result = ClusterDataFilters.applyConsumerGroupFilters(groups, filterName = "gamma")

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should filter groups case-insensitively`() {
            val groups = listOf(group("My-App-Group"), group("other-group"))

            val result = ClusterDataFilters.applyConsumerGroupFilters(groups, filterName = "my-app")

            assertEquals(listOf("My-App-Group"), result.map { it.consumerGroup })
        }
    }

    @Nested
    @DisplayName("applyConsumerGroupLimit")
    inner class ApplyConsumerGroupLimit {

        @Test
        fun `should truncate with hasMore true when over limit`() {
            val groups = listOf(group("a"), group("b"), group("c"))

            val (result, hasMore) = ClusterDataFilters.applyConsumerGroupLimit(groups, limit = 2)

            assertEquals(2, result.size)
            assertTrue(hasMore)
        }

        @Test
        fun `should return all with hasMore false when limit is null`() {
            val groups = listOf(group("a"), group("b"))

            val (result, hasMore) = ClusterDataFilters.applyConsumerGroupLimit(groups, limit = null)

            assertEquals(2, result.size)
            assertFalse(hasMore)
        }

        @Test
        fun `should return all with hasMore false when size is under limit`() {
            val groups = listOf(group("a"), group("b"))

            val (result, hasMore) = ClusterDataFilters.applyConsumerGroupLimit(groups, limit = 5)

            assertEquals(2, result.size)
            assertFalse(hasMore)
        }
    }

    @Nested
    @DisplayName("applySchemaFilters")
    inner class ApplySchemaFilters {

        @Test
        fun `should filter schemas by case-insensitive substring match`() {
            val schemas = listOf(schema("UserEvent"), schema("OrderCreated"), schema("user-profile"))

            val result = ClusterDataFilters.applySchemaFilters(schemas, filterName = "user")

            assertEquals(listOf("UserEvent", "user-profile"), result.map { it.name })
        }

        @Test
        fun `should return all schemas when filter is null`() {
            val schemas = listOf(schema("a"), schema("b"))

            val result = ClusterDataFilters.applySchemaFilters(schemas, filterName = null)

            assertEquals(2, result.size)
        }

        @Test
        fun `should return empty when no schemas match`() {
            val schemas = listOf(schema("alpha"), schema("beta"))

            val result = ClusterDataFilters.applySchemaFilters(schemas, filterName = "gamma")

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("sortSchemasWithFavorites")
    inner class SortSchemasWithFavorites {

        @Test
        fun `should sort pinned schemas first then alphabetically`() {
            val schemas = listOf(schema("zebra"), schema("alpha"), schema("mango"))

            val result = ClusterDataFilters.sortSchemasWithFavorites(schemas, pinnedSchemas = setOf("mango"), showFavoriteOnly = false)

            assertEquals(listOf("mango", "alpha", "zebra"), result.map { it.name })
            assertTrue(result[0].isFavorite)
            assertFalse(result[1].isFavorite)
        }

        @Test
        fun `should return only pinned schemas when showFavoriteOnly is true`() {
            val schemas = listOf(schema("a"), schema("b"), schema("c"))

            val result = ClusterDataFilters.sortSchemasWithFavorites(schemas, pinnedSchemas = setOf("b", "c"), showFavoriteOnly = true)

            assertEquals(listOf("b", "c"), result.map { it.name })
            assertTrue(result.all { it.isFavorite })
        }

        @Test
        fun `should sort all alphabetically with none marked favorite when pinned set is empty`() {
            val schemas = listOf(schema("cherry"), schema("apple"), schema("banana"))

            val result = ClusterDataFilters.sortSchemasWithFavorites(schemas, pinnedSchemas = emptySet(), showFavoriteOnly = false)

            assertEquals(listOf("apple", "banana", "cherry"), result.map { it.name })
            assertTrue(result.none { it.isFavorite })
        }
    }
}
