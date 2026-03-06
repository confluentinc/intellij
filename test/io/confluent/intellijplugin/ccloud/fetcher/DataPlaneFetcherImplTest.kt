package io.confluent.intellijplugin.ccloud.fetcher

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordData
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordHeader
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
class DataPlaneFetcherImplTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer
        private const val TEST_DATA_PLANE_TOKEN = "test-data-plane-token"
        private const val TEST_CLUSTER_ID = "lkc-test123"

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
        fetcher = DataPlaneFetcherImpl(
            kafkaClient = kafkaClient,
            schemaRegistryClient = null,
            clusterId = TEST_CLUSTER_ID,
            schemaRegistryId = null
        )
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun loadFixture(path: String): String {
        return javaClass.getResourceAsStream(path)!!.readBytes().toString(Charsets.UTF_8)
    }

    @Nested
    @DisplayName("produceRecord")
    inner class ProduceRecordTests {

        @Test
        fun `should produce record and return success response`() = runBlocking {
            val responseJson = loadFixture("/fixtures/producer/produce-record-response.json")
            wireMockServer.stubFor(
                post("/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/test-topic/records")
                    .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)
                    )
            )

            val request = ProduceRecordRequest(
                partitionId = 0,
                key = ProduceRecordData(type = "STRING", data = "my-key"),
                value = ProduceRecordData(type = "STRING", data = "my-value")
            )

            val response = fetcher.produceRecord("test-topic", request)

            assertEquals(200, response.errorCode)
            assertEquals("lkc-test123", response.clusterId)
            assertEquals("test-topic", response.topicName)
            assertEquals(0, response.partitionId)
            assertEquals(42, response.offset)
            assertEquals("STRING", response.key?.type)
            assertEquals("STRING", response.value?.type)
        }

        @Test
        fun `should send correct request body`() = runBlocking {
            val responseJson = loadFixture("/fixtures/producer/produce-record-response.json")
            wireMockServer.stubFor(
                post("/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/test-topic/records")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)
                    )
            )

            val request = ProduceRecordRequest(
                partitionId = 0,
                headers = listOf(ProduceRecordHeader("correlation-id", "dGVzdC12YWx1ZQ==")),
                key = ProduceRecordData(type = "STRING", data = "my-key"),
                value = ProduceRecordData(type = "BINARY", data = "SGVsbG8=")
            )

            fetcher.produceRecord("test-topic", request)

            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/test-topic/records"))
                    .withRequestBody(matchingJsonPath("$.partition_id", equalTo("0")))
                    .withRequestBody(matchingJsonPath("$.key.type", equalTo("STRING")))
                    .withRequestBody(matchingJsonPath("$.key.data", equalTo("my-key")))
                    .withRequestBody(matchingJsonPath("$.value.type", equalTo("BINARY")))
                    .withRequestBody(matchingJsonPath("$.value.data", equalTo("SGVsbG8=")))
                    .withRequestBody(matchingJsonPath("$.headers[0].name", equalTo("correlation-id")))
                    .withRequestBody(matchingJsonPath("$.headers[0].value", equalTo("dGVzdC12YWx1ZQ==")))
            )
        }

        @Test
        fun `should format URL path with topic name`() = runBlocking {
            val topicName = "my-special-topic"
            val responseJson = loadFixture("/fixtures/producer/produce-record-response.json")
            wireMockServer.stubFor(
                post("/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/$topicName/records")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)
                    )
            )

            val request = ProduceRecordRequest(
                value = ProduceRecordData(type = "STRING", data = "test")
            )

            fetcher.produceRecord(topicName, request)

            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/$topicName/records"))
            )
        }

        @Test
        fun `should parse error response`() = runBlocking {
            val responseJson = loadFixture("/fixtures/producer/produce-record-response-error.json")
            wireMockServer.stubFor(
                post("/kafka/v3/clusters/$TEST_CLUSTER_ID/topics/test-topic/records")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)
                    )
            )

            val request = ProduceRecordRequest(
                value = ProduceRecordData(type = "STRING", data = "test")
            )

            val response = fetcher.produceRecord("test-topic", request)

            assertEquals(404, response.errorCode)
            assertEquals("This server does not host this topic-partition.", response.message)
            assertNull(response.clusterId)
            assertNull(response.offset)
        }
    }
}