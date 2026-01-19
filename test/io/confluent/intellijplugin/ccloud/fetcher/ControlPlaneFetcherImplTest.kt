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
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
class ControlPlaneFetcherImplTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer
        private const val TEST_TOKEN = "test-control-plane-token"

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
            System.setProperty("ccloud.control-plane.base-url", "http://localhost:${wireMockServer.port()}")
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
            System.clearProperty("ccloud.control-plane.base-url")
        }
    }

    private lateinit var fetcher: ControlPlaneFetcherImpl
    private lateinit var authService: CCloudAuthService

    @BeforeEach
    fun setup() {
        authService = mock()
        whenever(authService.isSignedIn()).thenReturn(true)
        whenever(authService.getControlPlaneToken()).thenReturn(TEST_TOKEN)

        val client = CCloudRestClient(
            baseUrl = "http://localhost:${wireMockServer.port()}",
            authType = CCloudRestClient.AuthType.CONTROL_PLANE,
            authService = authService
        )
        fetcher = ControlPlaneFetcherImpl(client)
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun stubGetRequest(path: String, responseBody: String) {
        wireMockServer.stubFor(
            get(path)
                .withHeader("Authorization", equalTo("Bearer $TEST_TOKEN"))
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
    @DisplayName("getEnvironments")
    inner class GetEnvironmentsTests {

        @Test
        fun `fetches environments successfully`() = runBlocking {
            stubGetRequest("/org/v2/environments", loadMockResponse("list-environments.json"))

            val result = fetcher.getEnvironments()

            assertEquals(2, result.size)
            assertEquals("env-123", result[0].id)
            assertEquals("Development", result[0].displayName)
            assertEquals("env-456", result[1].id)
            assertEquals("Production", result[1].displayName)
        }

        @Test
        fun `handles missing display_name`() = runBlocking {
            stubGetRequest("/org/v2/environments", loadMockResponse("list-environments-with-missing-fields.json"))

            val result = fetcher.getEnvironments()

            assertEquals(1, result.size)
            assertEquals("env-123", result[0].id)
            assertEquals("env-123", result[0].displayName)  // Falls back to ID
        }

    }

    @Nested
    @DisplayName("getKafkaClusters")
    inner class GetKafkaClustersTests {

        @Test
        fun `fetches kafka clusters successfully`() = runBlocking {
            stubGetRequest("/cmk/v2/clusters?environment=env-123", loadMockResponse("list-clusters.json"))

            val result = fetcher.getKafkaClusters("env-123")

            assertEquals(2, result.size)
            assertEquals("lkc-abc123", result[0].id)
            assertEquals("Main Cluster", result[0].displayName)
            assertEquals("AWS", result[0].cloudProvider)
            assertEquals("us-east-1", result[0].region)
            assertEquals("lkc-def456", result[1].id)
            assertEquals("GCP", result[1].cloudProvider)
        }

        @Test
        fun `handles missing spec fields`() = runBlocking {
            stubGetRequest("/cmk/v2/clusters?environment=env-123", loadMockResponse("list-clusters-with-missing-fields.json"))

            val result = fetcher.getKafkaClusters("env-123")

            assertEquals(1, result.size)
            assertEquals("lkc-abc123", result[0].id)
            assertEquals("Unknown", result[0].cloudProvider)
            assertEquals("Unknown", result[0].region)
        }

    }

    @Nested
    @DisplayName("getSchemaRegistry")
    inner class GetSchemaRegistryTests {

        @Test
        fun `fetches schema registries successfully`() = runBlocking {
            stubGetRequest("/srcm/v3/clusters?environment=env-123", loadMockResponse("list-schema-registry.json"))

            val result = fetcher.getSchemaRegistry("env-123")

            assertEquals(1, result.size)
            assertEquals("lsrc-abc123", result[0].id)
            assertEquals("Main SR", result[0].displayName)
            assertEquals("AWS", result[0].cloudProvider)
            assertEquals("us-east-1", result[0].region)
            assertEquals("https://sr.us-east-1.aws.confluent.cloud", result[0].httpEndpoint)
        }

        @Test
        fun `handles missing spec fields in schema registry`() = runBlocking {
            stubGetRequest("/srcm/v3/clusters?environment=env-123", loadMockResponse("list-schema-registry-with-missing-fields.json"))

            val result = fetcher.getSchemaRegistry("env-123")

            assertEquals(1, result.size)
            assertEquals("lsrc-abc123", result[0].id)
            assertEquals("lsrc-abc123", result[0].displayName)  // Falls back to ID
            assertEquals("Unknown", result[0].cloudProvider)
            assertEquals("Unknown", result[0].region)
            assertEquals("", result[0].httpEndpoint)
        }

    }

}
