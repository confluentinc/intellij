package io.confluent.intellijplugin.scaffold.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.HttpRequests
import io.confluent.intellijplugin.scaffold.model.Scaffoldv1TemplateList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.SocketTimeoutException

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

        // Helper to extract fields from spec Map (since spec is typed as kotlin.Any)
        @Suppress("UNCHECKED_CAST")
        private fun getSpecField(spec: Any?, fieldName: String): Any? {
            if (spec == null) return null
            if (spec !is Map<*, *>) return null
            return (spec as? Map<String, Any?>)?.get(fieldName)
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
                            .withBody("""
                                {
                                    "api_version": "scaffold/v1",
                                    "kind": "TemplateList",
                                    "metadata": {
                                        "first": "string",
                                        "last": "string",
                                        "prev": "string",
                                        "next": "string",
                                        "total_size": 2
                                    },
                                    "data": [
                                        {
                                            "metadata": {
                                                "self": "https://api.confluent.cloud/scaffold/v1/templates/template-1",
                                                "resource_name": "crn://confluent.cloud/template=template-1"
                                            },
                                            "spec": {
                                                "name": "template-1",
                                                "display_name": "Template 1",
                                                "description": "First template",
                                                "version": "1.0.0",
                                                "language": "Java",
                                                "tags": ["kafka", "streams"]
                                            }
                                        },
                                        {
                                            "metadata": {
                                                "self": "https://api.confluent.cloud/scaffold/v1/templates/template-2",
                                                "resource_name": "crn://confluent.cloud/template=template-2"
                                            },
                                            "spec": {
                                                "name": "template-2",
                                                "display_name": "Template 2",
                                                "description": "Second template",
                                                "version": "2.0.0",
                                                "language": "Python",
                                                "tags": ["kafka", "producer"]
                                            }
                                        }
                                    ]
                                }
                            """.trimIndent())
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("vscode")
            }

            assertEquals(Scaffoldv1TemplateList.ApiVersion.scaffoldSlashV1, result.apiVersion)
            assertEquals(Scaffoldv1TemplateList.Kind.TemplateList, result.kind)
            assertEquals(2, result.data.size)

            val firstTemplate = result.data.first()
            assertNotNull(firstTemplate.spec)
            assertEquals("template-1", getSpecField(firstTemplate.spec, "name"))
            assertEquals("Template 1", getSpecField(firstTemplate.spec, "display_name"))
            assertEquals("First template", getSpecField(firstTemplate.spec, "description"))
            assertEquals("1.0.0", getSpecField(firstTemplate.spec, "version"))
            assertEquals("Java", getSpecField(firstTemplate.spec, "language"))
            assertEquals(listOf("kafka", "streams"), getSpecField(firstTemplate.spec, "tags"))
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
                            .withFixedDelay(65000) // Exceeds 60s read timeout
                            .withStatus(200)
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
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
            assertThrows(Exception::class.java) {
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
                            .withBody("""
                                {
                                    "api_version": "scaffold/v1",
                                    "kind": "TemplateList",
                                    "metadata": {
                                        "first": "string",
                                        "last": "string",
                                        "prev": "string",
                                        "next": "string",
                                        "total_size": 0
                                    },
                                    "data": []
                                }
                            """.trimIndent())
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("vscode")
            }

            assertEquals(Scaffoldv1TemplateList.ApiVersion.scaffoldSlashV1, result.apiVersion)
            assertEquals(Scaffoldv1TemplateList.Kind.TemplateList, result.kind)
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
                            .withBody("""
                                {
                                    "api_version": "scaffold/v1",
                                    "kind": "TemplateList",
                                    "metadata": {
                                        "first": "string",
                                        "last": "string",
                                        "prev": "string",
                                        "next": "string",
                                        "total_size": 1
                                    },
                                    "data": [
                                        {
                                            "metadata": {
                                                "self": "https://api.confluent.cloud/scaffold/v1/templates/minimal",
                                                "resource_name": "crn://confluent.cloud/template=minimal"
                                            },
                                            "spec": {
                                                "name": "minimal-template"
                                            }
                                        }
                                    ]
                                }
                            """.trimIndent())
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.fetchTemplates("vscode")
            }

            assertEquals(1, result.data.size)
            val firstTemplate = result.data.first()
            assertNotNull(firstTemplate.spec)
            assertEquals("minimal-template", getSpecField(firstTemplate.spec, "name"))
            assertNull(getSpecField(firstTemplate.spec, "display_name"))
            assertNull(getSpecField(firstTemplate.spec, "description"))
            assertNull(getSpecField(firstTemplate.spec, "version"))
            assertNull(getSpecField(firstTemplate.spec, "language"))
            assertNull(getSpecField(firstTemplate.spec, "tags"))
        }
    }
}
