package io.confluent.intellijplugin.ccloud.cache

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.ccloud.model.response.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*

/**
 * Tests for DataPlaneCache focusing on caching behavior.
 */
@TestApplication
class DataPlaneCacheTest {

    private lateinit var cluster: Cluster
    private lateinit var schemaRegistry: SchemaRegistry

    @BeforeEach
    fun setup() {
        cluster = Cluster(
            id = "lkc-test",
            displayName = "Test Cluster",
            cloudProvider = "AWS",
            region = "us-east-1",
            httpEndpoint = "https://test.confluent.cloud"
        )

        schemaRegistry = SchemaRegistry(
            id = "lsrc-test",
            displayName = "Test SR",
            cloudProvider = "AWS",
            region = "us-east-1",
            httpEndpoint = "https://sr.confluent.cloud"
        )
    }

    @Nested
    @DisplayName("hasSchemaRegistry")
    inner class HasSchemaRegistryTests {

        @Test
        fun `returns true when schema registry is configured`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            assertTrue(cache.hasSchemaRegistry())
        }

        @Test
        fun `returns false when schema registry is not configured`() {
            val cache = DataPlaneCache(cluster, null)

            assertFalse(cache.hasSchemaRegistry())
        }
    }

    @Nested
    @DisplayName("getTopics")
    inner class GetTopicsTests {

        @Test
        fun `returns empty list when cache is not populated`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val result = cache.getTopics()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getSchemas")
    inner class GetSchemasTests {

        @Test
        fun `returns empty list when cache is not populated`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val result = cache.getSchemas()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getFetcher")
    inner class GetFetcherTests {

        @Test
        fun `returns null when not connected`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val result = cache.getFetcher()

            assertEquals(null, result)
        }
    }

    @Nested
    @DisplayName("createTopic")
    inner class CreateTopicTests {

        @Test
        fun `adds topic to cache and getTopics reflects it`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val newTopic = TopicData(
                clusterId = "lkc-test",
                topicName = "new-topic",
                partitionsCount = 3,
                replicationFactor = 3
            )

            val request = CreateTopicRequest(
                topicName = "new-topic",
                partitionsCount = 3
            )

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            whenever(mockFetcher.createTopic(request)).thenReturn(newTopic)

            cache.createTopic(request)

            val topics = cache.getTopics()
            assertEquals(1, topics.size)
            assertEquals("new-topic", topics[0].topicName)
            assertEquals(3, topics[0].partitionsCount)
        }
    }

    @Nested
    @DisplayName("deleteTopic")
    inner class DeleteTopicTests {

        @Test
        fun `removes topic from cache and getTopics reflects it`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val topic1 = TopicData(clusterId = "lkc-test", topicName = "topic-1", partitionsCount = 3, replicationFactor = 3)
            val topic2 = TopicData(clusterId = "lkc-test", topicName = "topic-2", partitionsCount = 6, replicationFactor = 3)

            val cachedTopicsField = DataPlaneCache::class.java.getDeclaredField("cachedTopics")
            cachedTopicsField.isAccessible = true
            cachedTopicsField.set(cache, listOf(topic1, topic2))

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            whenever(mockFetcher.deleteTopic("topic-1")).thenReturn(Unit)

            cache.deleteTopic("topic-1")

            val topics = cache.getTopics()
            assertEquals(1, topics.size)
            assertEquals("topic-2", topics[0].topicName)
        }
    }

    @Nested
    @DisplayName("refreshTopics")
    inner class RefreshTopicsTests {

        @Test
        fun `replaces full cache`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val oldTopic = TopicData(clusterId = "lkc-test", topicName = "old-topic", partitionsCount = 1, replicationFactor = 3)

            val cachedTopicsField = DataPlaneCache::class.java.getDeclaredField("cachedTopics")
            cachedTopicsField.isAccessible = true
            cachedTopicsField.set(cache, listOf(oldTopic))

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            val newTopics = listOf(
                TopicData(clusterId = "lkc-test", topicName = "topic-1", partitionsCount = 3, replicationFactor = 3),
                TopicData(clusterId = "lkc-test", topicName = "topic-2", partitionsCount = 6, replicationFactor = 3)
            )

            whenever(mockFetcher.getTopics()).thenReturn(newTopics)

            cache.refreshTopics()

            val topics = cache.getTopics()
            assertEquals(2, topics.size)
            assertEquals("topic-1", topics[0].topicName)
            assertEquals("topic-2", topics[1].topicName)
            assertFalse(topics.any { it.topicName == "old-topic" })
        }
    }

    @Nested
    @DisplayName("Topic Enrichment")
    inner class TopicEnrichmentTests {

        @Test
        fun `updateTopicInCache stores enrichment data`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val enrichmentData = TopicEnrichmentData(messageCount = 1000L)
            cache.updateTopicInCache("test-topic", enrichmentData)

            val result = cache.getTopicEnrichment("test-topic")
            assertEquals(1000L, result?.messageCount)
        }

        @Test
        fun `getTopicEnrichment returns null for unenriched topic`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val result = cache.getTopicEnrichment("nonexistent-topic")

            assertEquals(null, result)
        }

        @Test
        fun `deleteTopic removes enrichment from cache`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val topic = TopicData(clusterId = "lkc-test", topicName = "test-topic", partitionsCount = 3, replicationFactor = 3)

            val cachedTopicsField = DataPlaneCache::class.java.getDeclaredField("cachedTopics")
            cachedTopicsField.isAccessible = true
            cachedTopicsField.set(cache, listOf(topic))

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            cache.updateTopicInCache("test-topic", TopicEnrichmentData(messageCount = 500L))
            assertEquals(500L, cache.getTopicEnrichment("test-topic")?.messageCount)

            whenever(mockFetcher.deleteTopic("test-topic")).thenReturn(Unit)

            cache.deleteTopic("test-topic")

            assertEquals(null, cache.getTopicEnrichment("test-topic"))
        }

        @Test
        fun `refreshTopics cleans stale enrichment`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            cache.updateTopicInCache("topic-1", TopicEnrichmentData(messageCount = 100L))
            cache.updateTopicInCache("topic-2", TopicEnrichmentData(messageCount = 200L))

            val newTopics = listOf(
                TopicData(clusterId = "lkc-test", topicName = "topic-1", partitionsCount = 3, replicationFactor = 3)
            )
            whenever(mockFetcher.getTopics()).thenReturn(newTopics)

            cache.refreshTopics()

            assertEquals(100L, cache.getTopicEnrichment("topic-1")?.messageCount)
            assertEquals(null, cache.getTopicEnrichment("topic-2"))
        }
    }

    @Nested
    @DisplayName("createSchema")
    inner class CreateSchemaTests {

        @Test
        fun `adds schema to cache and getSchemas reflects it`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val request = RegisterSchemaRequest(
                schema = """{"type":"record","name":"User","fields":[]}""",
                schemaType = "AVRO"
            )

            val response = RegisterSchemaResponse(id = 1)

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            whenever(mockFetcher.createSchema("user-schema", request)).thenReturn(response)

            cache.createSchema("user-schema", request)

            val schemas = cache.getSchemas()
            assertEquals(1, schemas.size)
            assertEquals("user-schema", schemas[0].name)
        }
    }

    @Nested
    @DisplayName("deleteSchema")
    inner class DeleteSchemaTests {

        @Test
        fun `removes schema from cache and getSchemas reflects it`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val schema1 = SchemaData(name = "user-schema", latestVersion = 1, schemaType = "AVRO")
            val schema2 = SchemaData(name = "order-schema", latestVersion = 2, schemaType = "PROTOBUF")

            val cachedSchemasField = DataPlaneCache::class.java.getDeclaredField("cachedSchemas")
            cachedSchemasField.isAccessible = true
            cachedSchemasField.set(cache, listOf(schema1, schema2))

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            whenever(mockFetcher.deleteSchema("user-schema", false)).thenReturn(listOf(1))

            cache.deleteSchema("user-schema", permanent = false)

            val schemas = cache.getSchemas()
            assertEquals(1, schemas.size)
            assertEquals("order-schema", schemas[0].name)
        }
    }

    @Nested
    @DisplayName("refreshSchemas")
    inner class RefreshSchemasTests {

        @Test
        fun `preserves enrichment for existing schemas`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val enrichedSchema = SchemaData(
                name = "user-schema",
                latestVersion = 5,
                schemaType = "AVRO",
                compatibility = "BACKWARD"
            )

            val cachedSchemasField = DataPlaneCache::class.java.getDeclaredField("cachedSchemas")
            cachedSchemasField.isAccessible = true
            cachedSchemasField.set(cache, listOf(enrichedSchema))

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            whenever(mockFetcher.getAllSubjects()).thenReturn(listOf("user-schema", "order-schema"))

            cache.refreshSchemas()

            val schemas = cache.getSchemas()
            assertEquals(2, schemas.size)

            val userSchema = schemas.find { it.name == "user-schema" }
            assertEquals(5, userSchema?.latestVersion)
            assertEquals("AVRO", userSchema?.schemaType)
            assertEquals("BACKWARD", userSchema?.compatibility)

            val orderSchema = schemas.find { it.name == "order-schema" }
            assertEquals(null, orderSchema?.latestVersion)
        }

        @Test
        fun `removes deleted schemas from cache`() = runBlocking {
            val cache = DataPlaneCache(cluster, schemaRegistry)
            val mockFetcher = mock<DataPlaneFetcherImpl>()

            val schema1 = SchemaData(name = "old-schema", latestVersion = 1)
            val schema2 = SchemaData(name = "user-schema", latestVersion = 5)

            val cachedSchemasField = DataPlaneCache::class.java.getDeclaredField("cachedSchemas")
            cachedSchemasField.isAccessible = true
            cachedSchemasField.set(cache, listOf(schema1, schema2))

            val fetcherField = DataPlaneCache::class.java.getDeclaredField("fetcher")
            fetcherField.isAccessible = true
            fetcherField.set(cache, mockFetcher)

            whenever(mockFetcher.getAllSubjects()).thenReturn(listOf("user-schema"))

            cache.refreshSchemas()

            val schemas = cache.getSchemas()
            assertEquals(1, schemas.size)
            assertEquals("user-schema", schemas[0].name)
            assertFalse(schemas.any { it.name == "old-schema" })
        }
    }

    @Nested
    @DisplayName("Schema Enrichment")
    inner class SchemaEnrichmentTests {

        @Test
        fun `updateSchemaInCache updates enrichment data`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val schema = SchemaData(name = "user-schema")

            val cachedSchemasField = DataPlaneCache::class.java.getDeclaredField("cachedSchemas")
            cachedSchemasField.isAccessible = true
            cachedSchemasField.set(cache, listOf(schema))

            val enrichmentData = SchemaEnrichmentData(
                latestVersion = 5,
                schemaType = "AVRO",
                compatibility = "BACKWARD"
            )
            cache.updateSchemaInCache("user-schema", enrichmentData)

            val schemas = cache.getSchemas()
            assertEquals(1, schemas.size)
            assertEquals(5, schemas[0].latestVersion)
            assertEquals("AVRO", schemas[0].schemaType)
            assertEquals("BACKWARD", schemas[0].compatibility)
        }

        @Test
        fun `updateSchemaInCache does not affect other schemas`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val schema1 = SchemaData(name = "user-schema", latestVersion = 1)
            val schema2 = SchemaData(name = "order-schema", latestVersion = 2)

            val cachedSchemasField = DataPlaneCache::class.java.getDeclaredField("cachedSchemas")
            cachedSchemasField.isAccessible = true
            cachedSchemasField.set(cache, listOf(schema1, schema2))

            val enrichmentData = SchemaEnrichmentData(
                latestVersion = 10,
                schemaType = "PROTOBUF",
                compatibility = "FULL"
            )
            cache.updateSchemaInCache("user-schema", enrichmentData)

            val schemas = cache.getSchemas()
            assertEquals(2, schemas.size)

            val userSchema = schemas.find { it.name == "user-schema" }
            assertEquals(10, userSchema?.latestVersion)
            assertEquals("PROTOBUF", userSchema?.schemaType)

            val orderSchema = schemas.find { it.name == "order-schema" }
            assertEquals(2, orderSchema?.latestVersion)
            assertEquals(null, orderSchema?.schemaType)
        }
    }

    @Nested
    @DisplayName("dispose")
    inner class DisposeTests {

        @Test
        fun `clears cache state`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val topic = TopicData(clusterId = "lkc-test", topicName = "test-topic", partitionsCount = 3, replicationFactor = 3)

            val cachedTopicsField = DataPlaneCache::class.java.getDeclaredField("cachedTopics")
            cachedTopicsField.isAccessible = true
            cachedTopicsField.set(cache, listOf(topic))

            assertEquals(1, cache.getTopics().size)

            cache.dispose()

            assertTrue(cache.getTopics().isEmpty())
        }

        @Test
        fun `clears enrichment cache`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            cache.updateTopicInCache("test-topic", TopicEnrichmentData(messageCount = 1000L))
            assertEquals(1000L, cache.getTopicEnrichment("test-topic")?.messageCount)

            cache.dispose()

            assertEquals(null, cache.getTopicEnrichment("test-topic"))
        }

        @Test
        fun `clears schema cache`() {
            val cache = DataPlaneCache(cluster, schemaRegistry)

            val schema = SchemaData(name = "test-schema", latestVersion = 5)

            val cachedSchemasField = DataPlaneCache::class.java.getDeclaredField("cachedSchemas")
            cachedSchemasField.isAccessible = true
            cachedSchemasField.set(cache, listOf(schema))

            assertEquals(1, cache.getSchemas().size)

            cache.dispose()

            assertTrue(cache.getSchemas().isEmpty())
        }
    }
}
