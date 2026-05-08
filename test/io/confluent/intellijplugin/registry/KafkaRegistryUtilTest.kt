package io.confluent.intellijplugin.registry

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.registry.confluent.ConfluentRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.RestService
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@TestApplication
class KafkaRegistryUtilTest {

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
    }

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/schemas/$name")!!.bufferedReader().use { it.readText() }.trim()

    private val addressSchemaJson = loadFixture("address-avro.json")
    private val userSchemaWithRefJson = loadFixture("user-avro-with-ref.json")
    private val simpleSchemaJson = loadFixture("user-avro-simple.json")
    private val addressProto = loadFixture("address-proto.proto")
    private val userProtoWithRef = loadFixture("user-proto-with-ref.proto")
    private val addressJsonSchema = loadFixture("address-json-schema.json")
    private val userJsonSchemaWithRef = loadFixture("user-json-schema-with-ref.json")

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    @Nested
    @DisplayName("loadSchema")
    inner class LoadSchema {

        @Test
        fun `should return null for non-registry field types`() {
            runBlocking {
                val dataManager = mock<BaseClusterDataManager>()

                for (fieldType in KafkaFieldType.defaultValues) {
                    val result = KafkaRegistryUtil.loadSchema("User", fieldType, dataManager)
                    assertNull(result)
                }

                verify(dataManager, never()).getLatestVersionInfo(any())
            }
        }

        @Test
        fun `should return null when schema not found`() {
            val dataManager = mock<BaseClusterDataManager> {
                on { runBlocking { getLatestVersionInfo("NonExistent") } } doReturn null
            }

            runBlocking {
                val result = KafkaRegistryUtil.loadSchema(
                    "NonExistent",
                    KafkaFieldType.SCHEMA_REGISTRY,
                    dataManager
                )

                assertNull(result)
            }
        }

        @Test
        fun `should parse simple schema without references`() {
            val versionInfo = SchemaVersionInfo(
                schemaName = "User",
                version = 1,
                type = KafkaRegistryFormat.AVRO,
                schema = simpleSchemaJson
            )

            val dataManager = mock<BaseClusterDataManager> {
                on { runBlocking { getLatestVersionInfo("User") } } doReturn versionInfo
                on { parseSchemaForDisplay(versionInfo) } doReturn
                    KafkaRegistryUtil.parseSchema(versionInfo.type, versionInfo.schema, versionInfo.references)
            }

            val result = runBlocking {
                KafkaRegistryUtil.loadSchema("User", KafkaFieldType.SCHEMA_REGISTRY, dataManager)
            }

            assertNotNull(result)
            assertEquals("com.example.User", result?.name())
        }

        @Nested
        @DisplayName("with schema references")
        inner class WithSchemaReferences {

            @Test
            fun `should parse schema with references via parseSchemaForDisplay`() {
                stubSchemaVersionLookup("Address", 1, addressSchemaJson)

                val references = listOf(SchemaReference("com.example.Address", "Address", 1))
                val versionInfo = SchemaVersionInfo(
                    schemaName = "User",
                    version = 1,
                    type = KafkaRegistryFormat.AVRO,
                    schema = userSchemaWithRefJson,
                    references = references
                )

                val registryClient = createRegistryClient()
                val dataManager = mock<BaseClusterDataManager> {
                    on { runBlocking { getLatestVersionInfo("User") } } doReturn versionInfo
                    on { parseSchemaForDisplay(versionInfo) } doReturn
                        KafkaRegistryUtil.parseSchema(
                            versionInfo.type, versionInfo.schema, registryClient, references
                        )
                }

                val result = runBlocking {
                    KafkaRegistryUtil.loadSchema("User", KafkaFieldType.SCHEMA_REGISTRY, dataManager)
                }

                assertNotNull(result)
                assertEquals("com.example.User", result?.name())
            }

            @Test
            fun `should resolve references with configured ConfluentRegistryClient`() {
                stubSchemaVersionLookup("Address", 1, addressSchemaJson)

                val registryClient = createRegistryClient()
                val references = listOf(SchemaReference("com.example.Address", "Address", 1))

                val result = KafkaRegistryUtil.parseSchema(
                    KafkaRegistryFormat.AVRO,
                    userSchemaWithRefJson,
                    registryClient,
                    references
                )

                assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
                assertEquals("com.example.User", result.getOrNull()?.name())
            }

            @Test
            fun `should fail with NPE when resolving references without registry client`() {
                val references = listOf(SchemaReference("com.example.Address", "Address", 1))

                val result = KafkaRegistryUtil.parseSchema(
                    KafkaRegistryFormat.AVRO,
                    userSchemaWithRefJson,
                    references
                )

                assertTrue(result.isFailure, "Expected failure for unconfigured providers with references")
                assertTrue(
                    result.exceptionOrNull() is NullPointerException,
                    "Expected NullPointerException but got: ${result.exceptionOrNull()}"
                )
            }

            @Test
            fun `should parse with pre-resolved references and no registry client`() {
                val references = listOf(SchemaReference("com.example.Address", "Address", 1))
                val resolvedReferences = mapOf("com.example.Address" to addressSchemaJson)

                val result = KafkaRegistryUtil.parseSchema(
                    KafkaRegistryFormat.AVRO,
                    userSchemaWithRefJson,
                    references,
                    resolvedReferences
                )

                assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
                assertEquals("com.example.User", result.getOrNull()?.name())
            }

            @Test
            fun `should fail with pre-resolved references when map is missing required ref`() {
                val references = listOf(SchemaReference("com.example.Address", "Address", 1))

                val result = KafkaRegistryUtil.parseSchema(
                    KafkaRegistryFormat.AVRO,
                    userSchemaWithRefJson,
                    references,
                    resolvedReferences = emptyMap()
                )

                assertTrue(result.isFailure, "Expected failure when a referenced type is not in the map")
            }

            @Test
            fun `should parse protobuf schema with pre-resolved references`() {
                val references = listOf(SchemaReference("Address.proto", "Address", 1))
                val resolvedReferences = mapOf("Address.proto" to addressProto)

                val result = KafkaRegistryUtil.parseSchema(
                    KafkaRegistryFormat.PROTOBUF,
                    userProtoWithRef,
                    references,
                    resolvedReferences
                )

                assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
            }

            @Test
            fun `should parse json schema with pre-resolved references`() {
                val references = listOf(SchemaReference("Address", "Address", 1))
                val resolvedReferences = mapOf("Address" to addressJsonSchema)

                val result = KafkaRegistryUtil.parseSchema(
                    KafkaRegistryFormat.JSON,
                    userJsonSchemaWithRef,
                    references,
                    resolvedReferences
                )

                assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()}")
            }
        }
    }

    private fun createRegistryClient(): ConfluentRegistryClient {
        val restService = RestService("http://localhost:${wireMockServer.port()}")
        return ConfluentRegistryClient(restService, emptyMap())
    }

    private fun stubSchemaVersionLookup(subject: String, version: Int, schemaJson: String) {
        val escapedJson = schemaJson.replace("\"", "\\\"")
        wireMockServer.stubFor(
            get(urlPathEqualTo("/subjects/$subject/versions/$version"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"subject":"$subject","version":$version,"id":1,"schemaType":"AVRO","schema":"$escapedJson"}"""
                        )
                )
        )
    }
}
