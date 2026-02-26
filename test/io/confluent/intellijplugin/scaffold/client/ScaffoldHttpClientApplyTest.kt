package io.confluent.intellijplugin.scaffold.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@TestApplication
class ScaffoldHttpClientApplyTest {

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

        private fun loadFixture(filename: String): String {
            val path = Paths.get("test/resources/scaffold-mock-responses/$filename")
            return Files.readString(path)
        }

        private fun createTestZip(): ByteArray {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("project/"))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("project/build.gradle"))
                zos.write("apply plugin: 'java'".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("project/src/Main.java"))
                zos.write("public class Main {}".toByteArray())
                zos.closeEntry()
            }
            return baos.toByteArray()
        }
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun baseUrl(): String = "http://localhost:${wireMockServer.port()}"

    @Nested
    @DisplayName("applyTemplate")
    inner class ApplyTemplate {

        @Test
        fun `successfully applies template and returns ZIP bytes`() {
            val zipBytes = createTestZip()

            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/java-starter/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/zip")
                            .withBody(zipBytes)
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val result = runBlocking {
                client.applyTemplate(
                    templateName = "java-starter",
                    options = mapOf("project_name" to "my-project")
                )
            }

            assertArrayEquals(zipBytes, result)
        }

        @Test
        fun `sends correct JSON body`() {
            val zipBytes = createTestZip()

            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/java-starter/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/zip")
                            .withBody(zipBytes)
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            runBlocking {
                client.applyTemplate(
                    templateName = "java-starter",
                    options = mapOf("project_name" to "my-project", "package" to "com.example")
                )
            }

            wireMockServer.verify(
                WireMock.postRequestedFor(
                    WireMock.urlEqualTo("/scaffold/v1/template-collections/intellij/templates/java-starter/apply")
                ).withRequestBody(WireMock.matchingJsonPath("$.options.project_name", WireMock.equalTo("my-project")))
                    .withRequestBody(WireMock.matchingJsonPath("$.options.package", WireMock.equalTo("com.example")))
            )
        }

        @Test
        fun `handles 400 bad request`() {
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/java-starter/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withBody(loadFixture("apply-template-error-400.txt"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.applyTemplate(
                        templateName = "java-starter",
                        options = emptyMap()
                    )
                }
            }

            assertEquals(400, exception.statusCode)
            assertTrue(exception.message?.contains("Missing required option") == true)
        }

        @Test
        fun `handles 404 template not found`() {
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/nonexistent/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(404)
                            .withBody(loadFixture("apply-template-error-404.txt"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.applyTemplate(
                        templateName = "nonexistent",
                        options = emptyMap()
                    )
                }
            }

            assertEquals(404, exception.statusCode)
        }

        @Test
        fun `handles 500 server error`() {
            wireMockServer.stubFor(
                WireMock.post("/scaffold/v1/template-collections/intellij/templates/java-starter/apply")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withBody(loadFixture("error-500-internal-server.txt"))
                    )
            )

            val client = ScaffoldHttpClient(baseUrl())
            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    client.applyTemplate(
                        templateName = "java-starter",
                        options = mapOf("project_name" to "test")
                    )
                }
            }

            assertEquals(500, exception.statusCode)
        }
    }
}
