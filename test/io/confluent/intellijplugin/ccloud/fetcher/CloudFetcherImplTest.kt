package io.confluent.intellijplugin.ccloud.fetcher

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
class CloudFetcherImplTest {

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build()

        private const val TEST_TOKEN = "test-control-plane-token"

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            System.setProperty("ccloud.control-plane.base-url", wireMock.baseUrl())
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            System.clearProperty("ccloud.control-plane.base-url")
        }
    }

    private lateinit var fetcher: CloudFetcherImpl
    private lateinit var authService: CCloudAuthService

    @BeforeEach
    fun setup() {
        authService = mock()
        whenever(authService.isSignedIn()).thenReturn(true)
        whenever(authService.getControlPlaneToken()).thenReturn(TEST_TOKEN)

        fetcher = CloudFetcherImpl(wireMock.baseUrl(), authService)
    }

    private fun stubGetRequest(path: String, responseBody: String) {
        wireMock.stubFor(
            get(path)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )
    }

    private fun buildEnvironmentJson(id: String, displayName: String? = null): String {
        val nameField = displayName?.let { ""","display_name": "$it"""" } ?: ""
        return """{
            "id": "$id"$nameField
        }"""
    }

    private fun buildKafkaClusterJson(
        id: String,
        displayName: String,
        cloud: String? = null,
        region: String? = null
    ): String {
        val spec = if (cloud != null && region != null) {
            ""","spec": {"cloud": "$cloud", "region": "$region"}"""
        } else {
            ""
        }
        return """{
            "id": "$id",
            "display_name": "$displayName"$spec
        }"""
    }

    private fun buildSchemaRegistryJson(
        id: String,
        displayName: String? = null,
        cloud: String? = null,
        region: String? = null,
        httpEndpoint: String? = null
    ): String {
        val specFields = mutableListOf<String>()
        displayName?.let { specFields.add(""""display_name": "$it"""") }
        cloud?.let { specFields.add(""""cloud": "$it"""") }
        region?.let { specFields.add(""""region": "$it"""") }
        httpEndpoint?.let { specFields.add(""""http_endpoint": "$it"""") }

        val spec = if (specFields.isNotEmpty()) {
            ""","spec": {${specFields.joinToString(",")}}"""
        } else {
            ""
        }
        return """{
            "id": "$id"$spec
        }"""
    }

    private fun wrapInDataResponse(vararg items: String): String {
        return """
            {
              "data": [${items.joinToString(",")}],
              "metadata": {"next": null}
            }
        """.trimIndent()
    }

    @Nested
    @DisplayName("getEnvironments")
    inner class GetEnvironmentsTests {

        @Test
        fun `fetches environments successfully`() = runBlocking {
            val response = wrapInDataResponse(
                buildEnvironmentJson("env-123", "Development"),
                buildEnvironmentJson("env-456", "Production")
            )
            stubGetRequest("/org/v2/environments", response)

            val result = fetcher.getEnvironments()

            assertEquals(2, result.size)
            assertEquals("env-123", result[0].id)
            assertEquals("Development", result[0].displayName)
            assertEquals("env-456", result[1].id)
            assertEquals("Production", result[1].displayName)
        }

        @Test
        fun `handles missing display_name`() = runBlocking {
            val response = wrapInDataResponse(buildEnvironmentJson("env-123"))
            stubGetRequest("/org/v2/environments", response)

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
            val response = wrapInDataResponse(
                buildKafkaClusterJson("lkc-abc123", "Main Cluster", "AWS", "us-east-1"),
                buildKafkaClusterJson("lkc-def456", "Secondary Cluster", "GCP", "us-central1")
            )
            stubGetRequest("/cmk/v2/clusters?environment=env-123", response)

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
            val response = wrapInDataResponse(
                buildKafkaClusterJson("lkc-abc123", "Test Cluster")
            )
            stubGetRequest("/cmk/v2/clusters?environment=env-123", response)

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
            val response = wrapInDataResponse(
                buildSchemaRegistryJson(
                    "lsrc-abc123",
                    "Main SR",
                    "AWS",
                    "us-east-1",
                    "https://sr.us-east-1.aws.confluent.cloud"
                )
            )
            stubGetRequest("/srcm/v3/clusters?environment=env-123", response)

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
            val response = wrapInDataResponse(buildSchemaRegistryJson("lsrc-abc123"))
            stubGetRequest("/srcm/v3/clusters?environment=env-123", response)

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
