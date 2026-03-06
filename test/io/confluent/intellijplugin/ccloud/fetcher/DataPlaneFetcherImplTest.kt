package io.confluent.intellijplugin.ccloud.fetcher

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.util.ResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
class DataPlaneFetcherImplTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer
        private const val TEST_DATA_PLANE_TOKEN = "test-data-plane-token"
        private const val TEST_CLUSTER_ID = "lkc-abc123"
        private const val TEST_SR_ID = "lsrc-xyz789"

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
        }
    }

    private lateinit var fetcher: DataPlaneFetcherImpl
    private lateinit var authService: CCloudAuthService

    @BeforeEach
    fun setup() {
        authService = mock()
        whenever(authService.isSignedIn()).thenReturn(true)
        whenever(authService.getDataPlaneToken()).thenReturn(TEST_DATA_PLANE_TOKEN)

        val kafkaClient = CCloudRestClient(
            baseUrl = "http://localhost:${wireMockServer.port()}",
            authType = CCloudRestClient.AuthType.DATA_PLANE,
            authService = authService
        )

        val schemaRegistryClient = CCloudRestClient(
            baseUrl = "http://localhost:${wireMockServer.port()}",
            authType = CCloudRestClient.AuthType.DATA_PLANE,
            additionalHeaders = mapOf("target-sr-cluster" to TEST_SR_ID),
            authService = authService
        )

        fetcher = DataPlaneFetcherImpl(
            kafkaClient = kafkaClient,
            schemaRegistryClient = schemaRegistryClient,
            clusterId = TEST_CLUSTER_ID,
            schemaRegistryId = TEST_SR_ID
        )
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun stubKafkaGet(path: String, responseBody: String) {
        wireMockServer.stubFor(
            get(path)
                .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )
    }

    private fun stubSchemaRegistryGet(path: String, responseBody: String) {
        wireMockServer.stubFor(
            get(path)
                .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN"))
                .withHeader("target-sr-cluster", equalTo(TEST_SR_ID))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )
    }

    private fun loadMockResponse(filename: String): String {
        return ResourceLoader.loadResource("ccloud-resources-mock-responses/$filename")
    }

    @Nested
    @DisplayName("getTopics")
    inner class GetTopicsTests {

        @Test
        fun `fetches topics successfully`() = runBlocking {
            stubKafkaGet("/kafka/v3/clusters/$TEST_CLUSTER_ID/topics", loadMockResponse("list-topics.json"))

            val result = fetcher.getTopics()

            assertEquals(3, result.size)
            assertEquals("orders", result[0].topicName)
            assertEquals(6, result[0].partitionsCount)
            assertEquals(3, result[0].replicationFactor)
            assertFalse(result[0].isInternal)

            assertEquals("payments", result[1].topicName)
            assertEquals("__consumer_offsets", result[2].topicName)
            assertTrue(result[2].isInternal)
        }

        @Test
        fun `handles empty topics list`() = runBlocking {
            stubKafkaGet(
                "/kafka/v3/clusters/$TEST_CLUSTER_ID/topics", """
                {
                  "kind": "KafkaTopicList",
                  "metadata": {"next": null},
                  "data": []
                }
            """.trimIndent()
            )

            val result = fetcher.getTopics()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("describeTopicPartitions")
    inner class DescribeTopicPartitionsTests {

        @Test
        fun `fetches topic partitions successfully`() = runBlocking {
            stubKafkaGet(
                "/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/orders/partitions",
                loadMockResponse("describe-topic-partitions.json")
            )

            val result = fetcher.describeTopicPartitions("orders")

            assertEquals(3, result.size)
            assertEquals(0, result[0].partitionId)
            assertEquals(1, result[1].partitionId)
            assertEquals(2, result[2].partitionId)
        }

        @Test
        fun `handles empty partitions list`() = runBlocking {
            stubKafkaGet(
                "/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/empty-topic/partitions",
                """{"kind": "KafkaPartitionList", "metadata": {"next": null}, "data": []}"""
            )

            val result = fetcher.describeTopicPartitions("empty-topic")

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getTopicMessageCount")
    inner class GetTopicMessageCountTests {

        @Test
        fun `fetches topic message count successfully`() = runBlocking {
            stubKafkaGet(
                "/kafka/v3/clusters/$TEST_CLUSTER_ID/internal/topics/orders/partitions/-/records:offsets",
                loadMockResponse("topic-message-count.json")
            )

            val result = fetcher.getTopicMessageCount("orders")

            assertEquals(12345L, result)
        }

        @Test
        fun `handles zero message count`() = runBlocking {
            stubKafkaGet(
                "/kafka/v3/clusters/$TEST_CLUSTER_ID/internal/topics/empty-topic/partitions/-/records:offsets",
                """{"total_records": 0}"""
            )

            val result = fetcher.getTopicMessageCount("empty-topic")

            assertEquals(0L, result)
        }
    }

    @Nested
    @DisplayName("getAllSubjects")
    inner class GetAllSubjectsTests {

        @Test
        fun `fetches all subjects successfully`() = runBlocking {
            stubSchemaRegistryGet("/subjects", loadMockResponse("list-all-subjects.json"))

            val result = fetcher.getAllSubjects()

            assertEquals(3, result.size)
            assertEquals("user-schema", result[0])
            assertEquals("order-schema", result[1])
            assertEquals("payment-schema", result[2])
        }

        @Test
        fun `handles empty subject list`() = runBlocking {
            stubSchemaRegistryGet("/subjects", "[]")

            val result = fetcher.getAllSubjects()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("listSchemaVersions")
    inner class ListSchemaVersionsTests {

        @Test
        fun `fetches schema versions successfully`() = runBlocking {
            stubSchemaRegistryGet("/subjects/user-schema/versions", loadMockResponse("schema-versions-list.json"))

            val result = fetcher.listSchemaVersions("user-schema")

            assertEquals(3, result.size)
            assertEquals(1L, result[0])
            assertEquals(2L, result[1])
            assertEquals(3L, result[2])
        }

        @Test
        fun `handles empty version list`() = runBlocking {
            stubSchemaRegistryGet("/subjects/empty-schema/versions", "[]")

            val result = fetcher.listSchemaVersions("empty-schema")

            assertTrue(result.isEmpty())
        }

        @Test
        fun `encodes subject name with special characters`() = runBlocking {
            stubSchemaRegistryGet("/subjects/user-value-schema/versions", "[1, 2]")

            val result = fetcher.listSchemaVersions("user-value-schema")

            assertEquals(2, result.size)
        }
    }

    @Nested
    @DisplayName("getSchemaVersionInfo")
    inner class GetSchemaVersionInfoTests {

        @Test
        fun `fetches specific schema version successfully`(): Unit = runBlocking {
            stubSchemaRegistryGet("/subjects/user-schema/versions/2", loadMockResponse("schema-version-specific.json"))

            val result = fetcher.getSchemaVersionInfo("user-schema", 2)

            assertEquals("user-schema", result.subject)
            assertEquals(2, result.version)
            assertEquals(100002, result.id)
            assertEquals("AVRO", result.schemaType)
            assertNotNull(result.schema)
        }

        @Test
        fun `handles schema with references`() = runBlocking {
            stubSchemaRegistryGet("/subjects/user-schema/versions/2", loadMockResponse("schema-version-specific.json"))

            val result = fetcher.getSchemaVersionInfo("user-schema", 2)

            assertNotNull(result.references)
            assertTrue(result.references!!.isEmpty())
        }
    }

    @Nested
    @DisplayName("getLatestVersionInfo")
    inner class GetLatestVersionInfoTests {

        @Test
        fun `fetches latest schema version successfully`(): Unit = runBlocking {
            stubSchemaRegistryGet(
                "/subjects/user-schema/versions/latest",
                loadMockResponse("schema-version-latest.json")
            )

            val result = fetcher.getLatestVersionInfo("user-schema")

            assertEquals("user-schema", result.subject)
            assertEquals(3, result.version)
            assertEquals(100001, result.id)
            assertEquals("AVRO", result.schemaType)
            assertNotNull(result.schema)
        }
    }

    @Nested
    @DisplayName("getSchemaIdInfo")
    inner class GetSchemaIdInfoTests {

        @Test
        fun `fetches schema by ID successfully`() = runBlocking {
            stubSchemaRegistryGet("/schemas/ids/100001", loadMockResponse("schema-by-id.json"))

            val result = fetcher.getSchemaIdInfo(100001)

            assertEquals("AVRO", result.schemaType)
            assertNotNull(result.schema)
            assertTrue(result.schema.contains("User"))
        }
    }

    @Nested
    @DisplayName("loadSchemaInfo")
    inner class LoadSchemaInfoTests {

        @Test
        fun `loads schema info with compatibility level`() = runBlocking {
            stubSchemaRegistryGet(
                "/subjects/user-schema/versions/latest",
                loadMockResponse("schema-version-latest.json")
            )
            stubSchemaRegistryGet("/config/user-schema", loadMockResponse("schema-compatibility.json"))

            val result = fetcher.loadSchemaInfo("user-schema")

            assertEquals("user-schema", result.name)
            assertEquals(3, result.latestVersion)
            assertEquals("AVRO", result.schemaType)
            assertEquals("BACKWARD", result.compatibility)
        }

        @Test
        fun `loads schema info without compatibility level when not available`(): Unit = runBlocking {
            stubSchemaRegistryGet(
                "/subjects/user-schema/versions/latest",
                loadMockResponse("schema-version-latest.json")
            )
            // Compatibility endpoint returns 404
            wireMockServer.stubFor(
                get("/config/user-schema")
                    .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN"))
                    .withHeader("target-sr-cluster", equalTo(TEST_SR_ID))
                    .willReturn(aResponse().withStatus(404))
            )

            val result = fetcher.loadSchemaInfo("user-schema")

            assertEquals("user-schema", result.name)
            assertEquals(3, result.latestVersion)
            assertEquals("AVRO", result.schemaType)
            assertEquals(null, result.compatibility)
        }

        @Test
        fun `handles errors gracefully and returns minimal schema data`() = runBlocking {
            // Latest version endpoint returns error
            wireMockServer.stubFor(
                get("/subjects/missing-schema/versions/latest")
                    .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN"))
                    .withHeader("target-sr-cluster", equalTo(TEST_SR_ID))
                    .willReturn(aResponse().withStatus(404))
            )

            val result = fetcher.loadSchemaInfo("missing-schema")

            // Returns minimal SchemaData with only name
            assertEquals("missing-schema", result.name)
            assertEquals(null, result.latestVersion)
            assertEquals(null, result.schemaType)
            assertEquals(null, result.compatibility)
        }
    }

    @Nested
    @DisplayName("getSubjectCompatibility")
    inner class GetSubjectCompatibilityTests {

        @Test
        fun `fetches subject compatibility successfully`() = runBlocking {
            stubSchemaRegistryGet("/config/user-schema", loadMockResponse("schema-compatibility.json"))

            val result = fetcher.getSubjectCompatibility("user-schema")

            assertEquals("BACKWARD", result.compatibilityLevel)
        }
    }
}
