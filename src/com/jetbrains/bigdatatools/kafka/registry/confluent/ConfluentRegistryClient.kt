package com.jetbrains.bigdatatools.kafka.registry.confluent

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.common.connection.tunnel.BdtSshTunnelService
import com.jetbrains.bigdatatools.common.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import io.confluent.kafka.schemaregistry.client.rest.RestService
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig

class ConfluentRegistryClient(restService: RestService) : Disposable {
  val internalClient = CachedSchemaRegistryClient(restService,
                                                  100,
                                                  KafkaRegistryUtil.registrySchemaProviders,
                                                  null, null)

  val allSubjects
    get() = internalClient.allSubjects.filterNotNull()

  override fun dispose() {}

  fun getAllSubjects(registryShowDeletedSubjects: Boolean) =
    internalClient.getAllSubjects(registryShowDeletedSubjects)?.toList() ?: emptyList()

  fun getLatestSchemaMetadata(schema: String): SchemaMetadata? {
    return internalClient.getLatestSchemaMetadata(schema)
  }

  fun deleteSchemaVersion(name: String, version: String, permanent: Boolean) {
    internalClient.deleteSchemaVersion(name, version, permanent)
  }

  fun deleteSubject(name: String, permanent: Boolean) {
    internalClient.deleteSubject(name, permanent)

  }

  fun register(schemaName: String, parsedSchema: ParsedSchema?) {
    internalClient.register(schemaName, parsedSchema)
  }

  fun getSchemaById(id: Int): ParsedSchema? {
    return internalClient.getSchemaById(id)
  }

  fun getAllVersions(schema: String): List<Int> {
    return internalClient.getAllVersions(schema)
  }

  fun getSchemaMetadata(schemaName: String, version: Int): SchemaMetadata? {
    return internalClient.getSchemaMetadata(schemaName, version)
  }

  fun parseSchema(schemaType: String, schema: String, references: List<SchemaReference>): ParsedSchema {
    return internalClient.parseSchema(schemaType, schema, references).get()
  }

  companion object {
    fun createFor(project: Project?, connectionData: KafkaConnectionData, testConnection: Boolean) =
      createConfluentClient(connectionData, project, testConnection)

    private fun createConfluentClient(connectionData: KafkaConnectionData,
                                      project: Project?,
                                      testConnection: Boolean): ConfluentRegistryClient? {
      val props = BdtPropertyComponent.parseProperties(connectionData.properties).associate {
        (it.name ?: "") to (it.value ?: "")
      } + BdtPropertyComponent.parseProperties(connectionData.registryProperties).associate {
        (it.name ?: "") to (it.value ?: "")
      }

      val url = props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG]?.ifBlank { null }
                ?: connectionData.registryUrl?.ifBlank { null } ?: return null

      val tunnel = BdtSshTunnelService.createIfRequired(project, connectionData.getTunnelData(),
                                                        url, connectionData.innerId,
                                                        testConnection)
      val tunneledUrl = tunnel?.tunnelledUri ?: url

      val restService = RestService(tunneledUrl)

      restService.configure(props)
      val registryClient = ConfluentRegistryClient(restService)
      if (tunnel != null) {
        Disposer.register(registryClient, tunnel)
      }
      return registryClient
    }
  }
}