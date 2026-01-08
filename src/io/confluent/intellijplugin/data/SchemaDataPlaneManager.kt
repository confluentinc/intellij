package io.confluent.intellijplugin.data

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.ccloud.client.DataPlaneRestClient
import io.confluent.intellijplugin.ccloud.fetcher.SchemaRegistryFetcherImpl
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import kotlinx.coroutines.runBlocking

/**
 * Manages Schema Registry data plane resources via REST API.
 *
 * Uses "target-sr-cluster" header for all requests.
 */
class SchemaDataPlaneManager(
    private val project: Project?,
    private val schemaRegistry: SchemaRegistry
) {
    private val log = Logger.getInstance(SchemaDataPlaneManager::class.java)

    // REST client with Schema Registry specific header
    private val restClient: DataPlaneRestClient by lazy {
        val endpoint = schemaRegistry.httpEndpoint.removeSuffix(":443")
        DataPlaneRestClient(
            endpoint,
            additionalHeaders = mapOf("target-sr-cluster" to schemaRegistry.id)
        )
    }

    private val fetcher: SchemaRegistryFetcherImpl by lazy {
        SchemaRegistryFetcherImpl(restClient, schemaRegistry.id)
    }

    /**
     * Get all subjects (schema names) for this Schema Registry.
     *
     * @return List of subject names
     */
    fun getSubjects(): List<String> = runBlocking {
        try {
            fetcher.listSubjects()
        } catch (e: Exception) {
            log.warn("Failed to fetch subjects for SR ${schemaRegistry.id}: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get all subjects with detailed information (version, type, etc.)
     *
     * @return List of SubjectData with metadata
     */
    fun getSubjectsWithDetails() = runBlocking {
        try {
            fetcher.listSubjectsWithDetails()
        } catch (e: Exception) {
            log.warn("Failed to fetch subject details for SR ${schemaRegistry.id}: ${e.message}")
            emptyList()
        }
    }
}
