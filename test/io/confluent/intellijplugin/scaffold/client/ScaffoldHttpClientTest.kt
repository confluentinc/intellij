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
        fun `successfully fetches templates for intellij collection`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-success.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("intellij")
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
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(500, exception.statusCode)
        }

        @Test
        fun `handles network timeouts`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
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
                    client.fetchTemplates("intellij")
                }
            }
        }

        @Test
        fun `handles malformed JSON responses`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
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
                    client.fetchTemplates("intellij")
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
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-empty.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("intellij")
            }

            assertTrue(result.data.isEmpty())
        }

        @Test
        fun `handles templates with null metadata fields`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-minimal.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("intellij")
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

        @Test
        fun `handles 400 bad request`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withBody("Bad Request: Invalid collection name")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(400, exception.statusCode)
            assertTrue(exception.message?.contains("Bad Request") == true)
        }

        @Test
        fun `handles 401 unauthorized`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(401)
                            .withBody("Unauthorized")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(401, exception.statusCode)
            assertTrue(exception.message?.contains("Unauthorized") == true)
        }

        @Test
        fun `handles 403 forbidden`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(403)
                            .withBody("Forbidden")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(403, exception.statusCode)
            assertTrue(exception.message?.contains("Forbidden") == true)
        }

        @Test
        fun `handles 500 error with empty body`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withBody("")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(500, exception.statusCode)
            assertEquals("Server error", exception.message)
        }

        @Test
        fun `handles 500 error with detailed error body`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withBody("Database connection failed")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(500, exception.statusCode)
            assertEquals("Server error: Database connection failed", exception.message)
        }

        @Test
        fun `handles 502 bad gateway`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(502)
                            .withBody("Bad Gateway")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(502, exception.statusCode)
            assertTrue(exception.message?.contains("Bad Gateway") == true)
        }

        @Test
        fun `handles 503 service unavailable`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(503)
                            .withBody("Service Unavailable")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.fetchTemplates("intellij")
                }
            }

            assertEquals(503, exception.statusCode)
        }

        @Test
        fun `handles templates with options`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-with-options.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("intellij")
            }

            assertEquals(1, result.data.size)
            val template = result.data.first()
            assertNotNull(template.spec.options)
            assertEquals(2, template.spec.options!!.size)
            val nameOption = template.spec.options!!["project_name"]
            assertNotNull(nameOption)
            assertEquals("Project Name", nameOption!!.displayName)
            assertEquals("my-project", nameOption.initialValue)
        }

        @Test
        fun `parses datetime and URI fields correctly`() {
            wireMockServer.stubFor(
                WireMock.get("/scaffold/v1/template-collections/intellij/templates")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(loadFixture("template-list-with-timestamps.json"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("intellij")
            }

            assertEquals(1, result.data.size)
            val template = result.data.first()

            // Verify URI parsing
            assertNotNull(template.metadata.self)
            assertEquals("https", template.metadata.self?.scheme)
            assertEquals("api.confluent.cloud", template.metadata.self?.host)

            // Verify OffsetDateTime parsing
            assertNotNull(template.metadata.createdAt)
            assertEquals(2024, template.metadata.createdAt?.year)
            assertEquals(1, template.metadata.createdAt?.monthValue)
            assertEquals(15, template.metadata.createdAt?.dayOfMonth)

            assertNotNull(template.metadata.updatedAt)
            assertEquals(2024, template.metadata.updatedAt?.year)
            assertEquals(2, template.metadata.updatedAt?.monthValue)

            // Verify null datetime field
            assertNull(template.metadata.deletedAt)

            // Verify list metadata URIs
            assertNotNull(result.metadata.first)
            assertNull(result.metadata.prev)
        }
    }

    @Nested
    @DisplayName("applyTemplate")
    inner class ApplyTemplate {

        @Test
        fun `successfully applies template and returns ZIP bytes`() {
            val zipContent = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02, 0x03)
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/my-template/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/zip")
                            .withBody(zipContent)
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.applyTemplate("my-template", options = mapOf("name" to "test"))
            }

            assertTrue(result.isNotEmpty())
            assertTrue(result.contentEquals(zipContent))
        }

        @Test
        fun `sends correct JSON request body`() {
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/test-template/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody(byteArrayOf(0x00))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            runBlocking {
                client.applyTemplate("test-template", options = mapOf("name" to "my-project", "lang" to "Java"))
            }

            wireMockServer.verify(
                WireMock.postRequestedFor(
                    WireMock.urlEqualTo("/scaffold/v1/template-collections/intellij/templates/test-template/apply")
                ).withRequestBody(WireMock.matchingJsonPath("$.options.name", WireMock.equalTo("my-project")))
                    .withRequestBody(WireMock.matchingJsonPath("$.options.lang", WireMock.equalTo("Java")))
            )
        }

        @Test
        fun `handles 400 bad request`() {
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/bad-template/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withBody("Invalid options")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.applyTemplate("bad-template", options = mapOf("name" to "test"))
                }
            }

            assertEquals(400, exception.statusCode)
        }

        @Test
        fun `handles 500 server error`() {
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/error-template/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withBody("Internal error")
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.applyTemplate("error-template", options = emptyMap())
                }
            }

            assertEquals(500, exception.statusCode)
        }

        @Test
        fun `uses custom collection name`() {
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/custom/templates/my-template/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody(byteArrayOf(0x00))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            runBlocking {
                client.applyTemplate("my-template", collectionName = "custom", options = emptyMap())
            }

            wireMockServer.verify(
                WireMock.postRequestedFor(
                    WireMock.urlEqualTo("/scaffold/v1/template-collections/custom/templates/my-template/apply")
                )
            )
        }
    }
}
