package io.confluent.intellijplugin.ccloud.fetcher

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.client.TEST_CONTROL_PLANE_TOKEN
import io.confluent.intellijplugin.ccloud.client.testClient
import io.confluent.intellijplugin.ccloud.client.ccloudResourceFixture
import io.confluent.intellijplugin.testutil.wireMockExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class ControlPlaneFetcherImplTest {

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock = wireMockExtension()
    }

    private lateinit var fetcher: ControlPlaneFetcherImpl

    @BeforeEach
    fun setup() {
        fetcher = ControlPlaneFetcherImpl(
            testClient(wireMock.baseUrl(), authType = CCloudRestClient.AuthType.CONTROL_PLANE)
        )
    }

    private fun stubGetRequest(path: String, responseBody: String) {
        wireMock.stubFor(
            get(path)
                .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                )
        )
    }

    @Nested
    @DisplayName("getEnvironments")
    inner class GetEnvironmentsTests {

        @Test
        fun `fetches environments successfully`() = runBlocking {
            stubGetRequest("/org/v2/environments", ccloudResourceFixture("list-environments.json"))

            val result = fetcher.getEnvironments()

            assertEquals(2, result.size)
            assertEquals("env-123", result[0].id)
            assertEquals("Development", result[0].displayName)
            assertEquals("env-456", result[1].id)
            assertEquals("Production", result[1].displayName)
        }

        @Test
        fun `handles missing display_name`() = runBlocking {
            stubGetRequest("/org/v2/environments", ccloudResourceFixture("list-environments-with-missing-fields.json"))

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
            stubGetRequest("/cmk/v2/clusters?environment=env-123", ccloudResourceFixture("list-clusters.json"))

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
            stubGetRequest("/cmk/v2/clusters?environment=env-123", ccloudResourceFixture("list-clusters-with-missing-fields.json"))

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
        fun `fetches schema registry successfully`() = runBlocking {
            stubGetRequest("/srcm/v3/clusters?environment=env-123", ccloudResourceFixture("list-schema-registry.json"))

            val result = fetcher.getSchemaRegistry("env-123")

            assertTrue(result != null)
            assertEquals("lsrc-abc123", result!!.id)
            assertEquals("Main SR", result.displayName)
            assertEquals("AWS", result.cloudProvider)
            assertEquals("us-east-1", result.region)
            assertEquals("https://sr.us-east-1.aws.confluent.cloud", result.httpEndpoint)
        }

        @Test
        fun `handles missing spec fields in schema registry`() = runBlocking {
            stubGetRequest("/srcm/v3/clusters?environment=env-123", ccloudResourceFixture("list-schema-registry-with-missing-fields.json"))

            val result = fetcher.getSchemaRegistry("env-123")

            assertTrue(result != null)
            assertEquals("lsrc-abc123", result!!.id)
            assertEquals("lsrc-abc123", result.displayName)  // Falls back to ID
            assertEquals("Unknown", result.cloudProvider)
            assertEquals("Unknown", result.region)
            assertEquals("", result.httpEndpoint)
        }

        @Test
        fun `returns null when no schema registry exists`() = runBlocking {
            stubGetRequest("/srcm/v3/clusters?environment=env-123", """{"data": [], "metadata": {"next": null}}""")

            val result = fetcher.getSchemaRegistry("env-123")

            assertTrue(result == null)
        }

    }

}
