package io.confluent.intellijplugin.ccloud.client

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.client.CloudRestClient.PageLimits
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
class CloudRestClientTest {

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build()

        private const val TEST_TOKEN = "test-bearer-token"
    }

    private lateinit var client: TestCloudRestClient
    private lateinit var authService: CCloudAuthService

    @BeforeEach
    fun setup() {
        authService = mock()
        whenever(authService.isSignedIn()).thenReturn(true)
        whenever(authService.getControlPlaneToken()).thenReturn(TEST_TOKEN)

        client = TestCloudRestClient(wireMock.baseUrl(), authService)
    }

    private suspend fun fetchItems(limits: PageLimits = PageLimits.DEFAULT): List<String> =
        client.listItems(
            headers = client.testHeaders(),
            uri = "/api/items",
            limits = limits,
            parser = client::parseStringList
        )

    private fun stubPage(items: List<String>, nextUrl: String? = null) {
        val itemsJson = items.joinToString(",") { "\"$it\"" }
        val nextJson = nextUrl?.let { "\"$it\"" } ?: "null"
        wireMock.stubFor(
            get("/api/items")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""{"data": [$itemsJson], "metadata": {"next": $nextJson}}""")
                )
        )
    }

    private fun stubError(statusCode: Int) {
        wireMock.stubFor(
            get("/api/items")
                .willReturn(aResponse().withStatus(statusCode))
        )
    }

    @Nested
    @DisplayName("listItems - Single Page")
    inner class SinglePageTests {

        @Test
        fun `fetches single page successfully`() = runBlocking {
            stubPage(listOf("item1", "item2", "item3"))

            val result = fetchItems()

            assertEquals(listOf("item1", "item2", "item3"), result)
            wireMock.verify(1, getRequestedFor(urlEqualTo("/api/items")))
        }
    }

    @Nested
    @DisplayName("listItems - Multiple Pages")
    inner class MultiplePageTests {

        @Test
        fun `fetches multiple pages until next is null`() = runBlocking {
            // Page 1
            wireMock.stubFor(
                get("/api/items")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["item1", "item2"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/api/items?page=2"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 2
            wireMock.stubFor(
                get(urlPathEqualTo("/api/items"))
                    .withQueryParam("page", equalTo("2"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["item3", "item4"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/api/items?page=3"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 3 (last)
            wireMock.stubFor(
                get(urlPathEqualTo("/api/items"))
                    .withQueryParam("page", equalTo("3"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["item5"],
                                  "metadata": {"next": null}
                                }
                            """.trimIndent())
                    )
            )

            val result = client.listItems(
                headers = client.testHeaders(),
                uri = "/api/items",
                limits = PageLimits.DEFAULT,
                parser = client::parseStringList
            )

            assertEquals(5, result.size)
            assertEquals(listOf("item1", "item2", "item3", "item4", "item5"), result)
            wireMock.verify(3, getRequestedFor(urlPathEqualTo("/api/items")))
        }

        @Test
        fun `detects and prevents pagination loops`() = runBlocking {
            // API returns same URL as next, creating infinite loop
            stubPage(listOf("item1"), "${wireMock.baseUrl()}/api/items")

            val exception = assertThrows<CloudApiException> { fetchItems() }

            assertTrue(exception.message!!.contains("loop detected"))
        }
    }

    @Nested
    @DisplayName("listItems - Pagination Limits")
    inner class PaginationLimitsTests {

        @Test
        fun `respects page limit`() = runBlocking {
            // Page 1
            wireMock.stubFor(
                get("/api/items")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["item1", "item2"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/api/items?page=2"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 2
            wireMock.stubFor(
                get(urlPathEqualTo("/api/items"))
                    .withQueryParam("page", equalTo("2"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["item3", "item4"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/api/items?page=3"}
                                }
                            """.trimIndent())
                    )
            )

            val result = client.listItems(
                headers = client.testHeaders(),
                uri = "/api/items",
                limits = PageLimits(maxPages = 2, maxItems = Int.MAX_VALUE),
                parser = client::parseStringList
            )

            // Should stop after 2 pages even though page 3 exists
            assertEquals(4, result.size)
            assertEquals(listOf("item1", "item2", "item3", "item4"), result)
            wireMock.verify(2, getRequestedFor(urlPathEqualTo("/api/items")))
        }

        @Test
        fun `respects item limit`() = runBlocking {
            // Page 1
            wireMock.stubFor(
                get("/api/items")
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["item1", "item2"],
                                  "metadata": {"next": "${wireMock.baseUrl()}/api/items?page=2"}
                                }
                            """.trimIndent())
                    )
            )

            // Page 2
            wireMock.stubFor(
                get(urlPathEqualTo("/api/items"))
                    .withQueryParam("page", equalTo("2"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody("""
                                {
                                  "data": ["item3", "item4", "item5"],
                                  "metadata": {"next": null}
                                }
                            """.trimIndent())
                    )
            )

            val result = client.listItems(
                headers = client.testHeaders(),
                uri = "/api/items",
                limits = PageLimits(maxPages = Int.MAX_VALUE, maxItems = 4),
                parser = client::parseStringList
            )

            // Should get 2 from page 1 + 2 from page 2 = 4 total (item limit)
            assertEquals(4, result.size)
            assertEquals(listOf("item1", "item2", "item3", "item4"), result)
        }
    }

    @Nested
    @DisplayName("listItems - Error Handling")
    inner class ErrorHandlingTests {

        @Test
        fun `throws CloudApiException on error`() = runBlocking {
            stubError(403)

            val exception = assertThrows<CloudApiException> { fetchItems() }

            assertEquals(403, exception.statusCode)
        }
    }

    /**
     * Test implementation of CloudRestClient for testing purposes.
     */
    private class TestCloudRestClient(
        baseUrl: String,
        private val mockAuthService: CCloudAuthService
    ) : CloudRestClient(baseUrl) {

        override fun getAuthHeaders(): Map<String, String> {
            if (!mockAuthService.isSignedIn()) {
                throw IllegalStateException("Not signed in")
            }

            val token = mockAuthService.getControlPlaneToken()
                ?: throw IllegalStateException("No token")

            return mapOf(
                "Authorization" to "Bearer $token",
                "Content-Type" to "application/json"
            )
        }

        fun testHeaders() = getAuthHeaders()

        /**
         * Test parser that extracts string list from JSON response.
         */
        fun parseStringList(json: String): Pair<List<String>, String?> {
            // Simple JSON parsing for test purposes
            val dataMatch = Regex(""""data"\s*:\s*\[(.*?)]""").find(json)
            val items = if (dataMatch != null) {
                val dataContent = dataMatch.groupValues[1]
                if (dataContent.isBlank()) {
                    emptyList()
                } else {
                    dataContent.split(",")
                        .map { it.trim().removeSurrounding("\"") }
                }
            } else {
                emptyList()
            }

            val nextMatch = Regex(""""next"\s*:\s*"([^"]*?)"""").find(json)
            val nextUrl = nextMatch?.groupValues?.get(1)

            return items to nextUrl
        }

        // Expose protected method for testing
        public override suspend fun <T> listItems(
            headers: Map<String, String>,
            uri: String,
            limits: PageLimits,
            parser: (String) -> Pair<List<T>, String?>
        ): List<T> = super.listItems(headers, uri, limits, parser)
    }
}
