package io.confluent.intellijplugin.ccloud.client

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient.PageLimits
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class CCloudRestClientTest {

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock = wireMockExtension()
    }

    private lateinit var client: CCloudRestClient

    @BeforeEach
    fun setup() {
        client = testClient(wireMock.baseUrl(), authType = CCloudRestClient.AuthType.CONTROL_PLANE)
    }

    private suspend fun fetchEnvironments(limits: PageLimits = PageLimits.DEFAULT): List<String> =
        client.fetchList(
            path = "/org/v2/environments",
            limits = limits,
            parser = ::parseEnvironmentIds
        )

    private fun parseEnvironmentIds(json: String): Pair<List<String>, String?> {
        val dataMatch = Regex(""""data"\s*:\s*\[(.*?)]""").find(json)
        val envIds = if (dataMatch != null) {
            val content = dataMatch.groupValues[1]
            if (content.isBlank()) emptyList()
            else content.split(",").map { it.trim().removeSurrounding("\"") }
        } else {
            emptyList()
        }

        val nextMatch = Regex(""""next"\s*:\s*"([^"]*?)"""").find(json)
        return envIds to nextMatch?.groupValues?.get(1)
    }

    private fun stubEnvironmentsPage(envIds: List<String>, nextUrl: String? = null) {
        val dataJson = envIds.joinToString(",") { "\"$it\"" }
        val nextJson = nextUrl?.let { "\"$it\"" } ?: "null"
        wireMock.stubFor(
            get("/org/v2/environments")
                .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""{"data": [$dataJson], "metadata": {"next": $nextJson}}""")
                )
        )
    }

    private fun stubEnvironmentsError(statusCode: Int) {
        wireMock.stubFor(
            get("/org/v2/environments")
                .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                .willReturn(aResponse().withStatus(statusCode))
        )
    }

    @Nested
    @DisplayName("fetchList - Single Page")
    inner class SinglePageTests {

        @Test
        fun `fetches single page successfully`() = runBlocking {
            stubEnvironmentsPage(listOf("env-abc123", "env-def456", "env-ghi789"))

            val result = fetchEnvironments()

            assertEquals(listOf("env-abc123", "env-def456", "env-ghi789"), result)
            wireMock.verify(1, getRequestedFor(urlEqualTo("/org/v2/environments"))
                .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN")))
        }
    }

    @Nested
    @DisplayName("fetchList - Multiple Pages")
    inner class MultiplePageTests {

        @Test
        fun `fetches multiple pages until next is null`() = runBlocking {
            // Page 1
            wireMock.stubFor(
                get("/org/v2/environments")
                    .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["env-abc123", "env-def456"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/org/v2/environments?page=2"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 2
            wireMock.stubFor(
                get(urlPathEqualTo("/org/v2/environments"))
                    .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                    .withQueryParam("page", equalTo("2"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["env-ghi789", "env-jkl012"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/org/v2/environments?page=3"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 3 (last)
            wireMock.stubFor(
                get(urlPathEqualTo("/org/v2/environments"))
                    .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                    .withQueryParam("page", equalTo("3"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["env-mno345"],
                                  "metadata": {"next": null}
                                }
                            """.trimIndent())
                    )
            )

            val result = client.fetchList<String>(
                path = "/org/v2/environments",
                limits = PageLimits.DEFAULT,
                parser = ::parseEnvironmentIds
            )

            assertEquals(5, result.size)
            assertEquals(listOf("env-abc123", "env-def456", "env-ghi789", "env-jkl012", "env-mno345"), result)
            wireMock.verify(3, getRequestedFor(urlPathEqualTo("/org/v2/environments"))
                .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN")))
        }

        @Test
        fun `detects and prevents pagination loops`() = runBlocking {
            // API returns same URL as next, creating infinite loop
            stubEnvironmentsPage(listOf("env-abc123"), "${wireMock.baseUrl()}/org/v2/environments")

            val exception = assertThrows<IllegalStateException> { fetchEnvironments() }

            assertTrue(exception.message!!.contains("loop detected"))
        }
    }

    @Nested
    @DisplayName("fetchList - Pagination Limits")
    inner class PaginationLimitsTests {

        @Test
        fun `respects page limit`() = runBlocking {
            // Page 1
            wireMock.stubFor(
                get("/org/v2/environments")
                    .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["env-abc123", "env-def456"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/org/v2/environments?page=2"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 2
            wireMock.stubFor(
                get(urlPathEqualTo("/org/v2/environments"))
                    .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                    .withQueryParam("page", equalTo("2"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["env-ghi789", "env-jkl012"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/org/v2/environments?page=3"}
                                }
                            """.trimIndent())
                    )
            )

            val result = client.fetchList<String>(
                path = "/org/v2/environments",
                limits = PageLimits(maxPages = 2, maxItems = Int.MAX_VALUE),
                parser = ::parseEnvironmentIds
            )

            // Should stop after 2 pages even though page 3 exists
            assertEquals(4, result.size)
            assertEquals(listOf("env-abc123", "env-def456", "env-ghi789", "env-jkl012"), result)
            wireMock.verify(2, getRequestedFor(urlPathEqualTo("/org/v2/environments"))
                .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN")))
        }

        @Test
        fun `respects item limit`() = runBlocking {
            // Page 1
            wireMock.stubFor(
                get("/org/v2/environments")
                    .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["env-abc123", "env-def456"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/org/v2/environments?page=2"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 2
            wireMock.stubFor(
                get(urlPathEqualTo("/org/v2/environments"))
                    .withHeader("Authorization", equalTo("Bearer $TEST_CONTROL_PLANE_TOKEN"))
                    .withQueryParam("page", equalTo("2"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["env-ghi789", "env-jkl012", "env-mno345"],
                                  "metadata": {"next": null}
                                }
                            """.trimIndent())
                    )
            )

            val result = client.fetchList<String>(
                path = "/org/v2/environments",
                limits = PageLimits(maxPages = Int.MAX_VALUE, maxItems = 4),
                parser = ::parseEnvironmentIds
            )

            // Should get 2 from page 1 + 2 from page 2 = 4 total (item limit)
            assertEquals(4, result.size)
            assertEquals(listOf("env-abc123", "env-def456", "env-ghi789", "env-jkl012"), result)
        }
    }

    @Nested
    @DisplayName("fetchList - Error Handling")
    inner class ErrorHandlingTests {

        @Test
        fun `throws CloudApiException on error`() = runBlocking {
            stubEnvironmentsError(403)

            val exception = assertThrows<CCloudApiException> { fetchEnvironments() }

            assertEquals(403, exception.statusCode)
        }
    }

    @Nested
    @DisplayName("User-Agent Header")
    inner class UserAgentTests {

        @Test
        fun `includes User-Agent header in requests`() = runBlocking {
            stubEnvironmentsPage(listOf("env-abc123"))

            fetchEnvironments()

            wireMock.verify(getRequestedFor(urlEqualTo("/org/v2/environments"))
                .withHeader("User-Agent", matching("confluent-for-intellij/v.+ \\(https://confluent\\.io; support@confluent\\.io\\)")))
        }
    }

    @Nested
    @DisplayName("Data Plane Authentication")
    inner class DataPlaneTests {

        @Test
        fun `uses data plane token for authentication`() = runBlocking {
            val kafkaClient = testClient(wireMock.baseUrl())

            wireMock.stubFor(
                get("/kafka/v3/clusters/lkc-abc123/topics")
                    .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN"))
                    .willReturn(aResponse().withStatus(200)
                        .withBody("""{"data": ["topic-1"], "metadata": {"next": null}}"""))
            )

            val result = kafkaClient.fetchList("/kafka/v3/clusters/lkc-abc123/topics", parser = ::parseEnvironmentIds)

            assertEquals(listOf("topic-1"), result)
            wireMock.verify(getRequestedFor(urlEqualTo("/kafka/v3/clusters/lkc-abc123/topics"))
                .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN")))
        }

        @Test
        fun `includes additional headers for schema registry`() = runBlocking {
            val srClient = testClient(
                wireMock.baseUrl(),
                additionalHeaders = mapOf("target-sr-cluster" to "lsrc-abc123")
            )

            wireMock.stubFor(
                get("/subjects")
                    .withHeader("Authorization", equalTo("Bearer $TEST_DATA_PLANE_TOKEN"))
                    .withHeader("target-sr-cluster", equalTo("lsrc-abc123"))
                    .willReturn(aResponse().withStatus(200)
                        .withBody("""{"data": ["my-subject"], "metadata": {"next": null}}"""))
            )

            val result = srClient.fetchList("/subjects", parser = ::parseEnvironmentIds)

            assertEquals(listOf("my-subject"), result)
        }
    }

}
