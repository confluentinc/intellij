package com.jetbrains.bigdatatools.kafka.registry.confluent

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.common.connection.tunnel.BdtSshTunnelService
import com.jetbrains.bigdatatools.common.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.SchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.registry.common.KafkaSchemaInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.RestService
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig

class ConfluentRegistryClient(restService: RestService) : Disposable {
  val internalClient = CachedSchemaRegistryClient(restService,
                                                  100,
                                                  KafkaRegistryUtil.registrySchemaProviders,
                                                  null, null)

  override fun dispose() {}

  fun checkConnection() {
    internalClient.mode
  }

  fun listSchemas(registryShowDeletedSubjects: Boolean): List<KafkaSchemaInfo> {
    val names = internalClient.getAllSubjects(registryShowDeletedSubjects)?.sortedBy { it.lowercase() } ?: emptyList()
    return names.map { KafkaSchemaInfo(it) }
  }

  fun loadSchemaInfo(schemaName: String): KafkaSchemaInfo {
    val meta = try {
      internalClient.getLatestSchemaMetadata(schemaName)
    }
    catch (t: Throwable) {
      thisLogger().warn("Cannot request schemaMeta", t)
      null
    }
    meta ?: return KafkaSchemaInfo(schemaName)

    return KafkaSchemaInfo(name = schemaName,
                           type = KafkaRegistryFormat.parse(meta.schemaType),
                           versions = meta.version.toLong())
  }

  fun deleteSchemaVersion(registryInfo: SchemaVersionInfo, isPermanent: Boolean = false) {
    internalClient.deleteSchemaVersion(registryInfo.schemaName, registryInfo.version.toString(), isPermanent)
  }

  fun deleteSchema(name: String, permanent: Boolean) {
    internalClient.deleteSubject(name, permanent)

  }

  fun createSchema(schemaName: String, parsedSchema: ParsedSchema) {
    internalClient.register(schemaName, parsedSchema)
  }


  fun updateSchema(registryInfo: SchemaVersionInfo, newText: @NlsSafe String) {
    val parsedSchema = KafkaRegistryUtil.parseSchema(registryInfo.type, newText, registryInfo.references).getOrThrow()
    internalClient.register(registryInfo.schemaName, parsedSchema)
  }


  fun listSchemaVersions(schema: String) = internalClient.getAllVersions(schema).map { it.toLong() }

  fun getSchemaVersionInfo(schema: String, version: Long): SchemaVersionInfo {
    val response = internalClient.getByVersion(schema, version.toInt(), true)
    return SchemaVersionInfo(schemaName = schema,
                             version = version,
                             type = KafkaRegistryFormat.parse(response.schemaType),
                             schema = response.schema,
                             references = response.references.toList())
  }

  fun getLatestVersionInfo(schemaName: String): SchemaVersionInfo {
    val metadata = internalClient.getLatestSchemaMetadata(schemaName)
    return SchemaVersionInfo(schemaName = schemaName,
                             version = metadata.version.toLong(),
                             type = KafkaRegistryFormat.parse(metadata.schemaType),
                             schema = metadata.schema)
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