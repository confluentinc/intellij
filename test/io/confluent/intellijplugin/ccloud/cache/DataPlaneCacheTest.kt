package io.confluent.intellijplugin.ccloud.cache

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

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
}
