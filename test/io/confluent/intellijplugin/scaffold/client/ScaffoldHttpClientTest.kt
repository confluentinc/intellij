package io.confluent.intellijplugin.scaffold.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.HttpRequests
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Paths

@TestApplication
class ScaffoldHttpClientTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer

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

        // Helper to load fixture files
        private fun loadFixture(filename: String): String {
            val path = Paths.get("test/resources/scaffold-mock-responses/$filename")
            return Files.readString(path)
        }
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun baseUrl(): String = "http://localhost:${wireMockServer.port()}"

    @Nested
    @DisplayName("fetchTemplates")
    inner class FetchTemplates {

        @Test
        fun `successfully fetches templates for vscode collection`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/vscode/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-success.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("vscode")
            }

            assertEquals(2, result.data.size)

            val template1 = result.data.firstOrNull { it.spec.name == "template-1" }
            assertNotNull(template1)
            assertNotNull(template1!!.spec)
            assertEquals("template-1", template1.spec.name)
            assertEquals("Template 1", template1.spec.displayName)
            assertEquals("First template", template1.spec.description)
            assertEquals("1.0.0", template1.spec.version)
            assertEquals("Java", template1.spec.language)
            assertEquals(listOf("kafka", "streams"), template1.spec.tags)
        }

        @Test
        fun `handles 500 server errors`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/vscode/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("vscode")
                }
            }

            assertEquals(500, exception.statusCode)
        }

        @Test
        fun `handles network timeouts`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/vscode/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withFixedDelay(500) // Exceeds test read timeout
                            .withStatus(200)
                    )
            )

            val client = ScaffoldHttpClient(
                baseUrl = baseUrl(),
                connectTimeoutMs = 100,
                readTimeoutMs = 200
            )
            assertThrows(SocketTimeoutException::class.java) {
                runBlocking {
                    client.fetchTemplates("vscode")
                }
            }
        }

        @Test
        fun `handles malformed JSON responses`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/vscode/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{invalid json")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            assertThrows(JsonEncodingException::class.java) {
                runBlocking {
                    client.fetchTemplates("vscode")
                }
            }
        }

        @Test
        fun `handles 404 for nonexistent collection`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/nonexistent/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(404)
                            .withBody("Collection not found")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("nonexistent")
                }
            }

            assertEquals(404, exception.statusCode)
        }

        @Test
        fun `handles empty template list`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/vscode/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-empty.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("vscode")
            }

            assertTrue(result.data.isEmpty())
        }

        @Test
        fun `handles templates with null metadata fields`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/vscode/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-minimal.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("vscode")
            }

            assertEquals(1, result.data.size)
            val firstTemplate = result.data.first()
            assertNotNull(firstTemplate.spec)
            assertEquals("minimal-template", firstTemplate.spec.name)
            assertNull(firstTemplate.spec.displayName)
            assertNull(firstTemplate.spec.description)
            assertNull(firstTemplate.spec.version)
            assertNull(firstTemplate.spec.language)
            assertNull(firstTemplate.spec.tags)
        }
    }
}
