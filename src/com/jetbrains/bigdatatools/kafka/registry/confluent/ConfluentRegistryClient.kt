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
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.schemaregistry.client.rest.RestService
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.common.config.ConfigDef

class ConfluentRegistryClient(restService: RestService, props: Map<String, String>) : Disposable {
  val internalClient = CachedSchemaRegistryClient(restService,
                                                  100,
                                                  KafkaRegistryUtil.registrySchemaProviders,
                                                  props, null)

  override fun dispose() {}

  fun checkConnection() {
    internalClient.mode
  }

  fun listSchemas(limit: Int?, filter: String?, registryShowDeletedSubjects: Boolean): Pair<List<KafkaSchemaInfo>, Boolean> {
    val names = internalClient.getAllSubjects(registryShowDeletedSubjects)?.sortedBy { it.lowercase() } ?: emptyList()
    val regex = filter?.let { Regex(it) }
    val filteredNames = names.filter { regex == null || it.contains(regex) }
    return filteredNames.map { KafkaSchemaInfo(it) }.take(limit ?: Int.MAX_VALUE) to (limit != null && names.size > limit)
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
                           version = meta.version.toLong())
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
    try {
      internalClient.register(registryInfo.schemaName, parsedSchema)
    }
    catch (t: Throwable) {
      throw t
    }
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
      val brokerSettings = BdtPropertyComponent.parseProperties(connectionData.properties).associate {
        (it.name ?: "") to (it.value ?: "")
      }
      val brokerSsl = if (connectionData.registryUseBrokerSsl) {
        val configDef = ConfigDef()
        configDef.withClientSslSupport()
        configDef.configKeys().mapNotNull {
          val key = it.key
          brokerSettings[key]?.let { (SchemaRegistryClientConfig.CLIENT_NAMESPACE + key) to it }
        }.toMap()
      }
      else
        mapOf()
      val registryProps = BdtPropertyComponent.parseProperties(connectionData.registryProperties).associate {
        (it.name ?: "") to (it.value ?: "")
      }
      val props = brokerSettings + brokerSsl + registryProps

      val url = props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG]?.ifBlank { null }
                ?: connectionData.registryUrl?.ifBlank { null } ?: return null

      val tunnel = BdtSshTunnelService.createIfRequired(project, connectionData.getTunnelData(),
                                                        url, connectionData.innerId,
                                                        testConnection)
      val tunneledUrl = tunnel?.tunnelledUri ?: url

      val restService = RestService(tunneledUrl)

      restService.configure(props)
      val registryClient = ConfluentRegistryClient(restService, props)
      if (tunnel != null) {
        Disposer.register(registryClient, tunnel)
      }
      return registryClient
    }
  }
}