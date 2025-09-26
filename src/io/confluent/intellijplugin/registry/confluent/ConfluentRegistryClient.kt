package io.confluent.intellijplugin.registry.confluent

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import io.confluent.intellijplugin.core.connection.exception.BdtConfigurationException
import io.confluent.intellijplugin.core.connection.tunnel.BdtSshTunnelService
import io.confluent.intellijplugin.core.rfs.driver.runBlockingInterruptible
import io.confluent.intellijplugin.core.settings.components.BdtPropertyComponent
import io.confluent.intellijplugin.data.KafkaDataManager.Companion.sortedSchemas
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.schemaregistry.ParsedSchema
import io.confluent.intellijplugin.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.intellijplugin.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.intellijplugin.schemaregistry.client.rest.RestService
import io.confluent.intellijplugin.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.common.config.ConfigDef

class ConfluentRegistryClient(restService: RestService, props: Map<String, String>) : Disposable {
  val internalClient = CachedSchemaRegistryClient(restService,
                                                  100,
                                                  KafkaRegistryUtil.getRegistrySchemaProviders(),
                                                  props, null)

  override fun dispose() {}

  fun checkConnection() {
    runBlockingInterruptible {
      internalClient.getAllSubjects()
    }
  }

  fun listSchemas(limit: Int?,
                  filter: String?,
                  registryShowDeletedSubjects: Boolean,
                  connectionId: String): Pair<List<KafkaSchemaInfo>, Boolean> {
    val names = internalClient.getAllSubjects(registryShowDeletedSubjects)?.sortedBy { it.lowercase() } ?: emptyList()
    val regex = filter?.let { Regex(it) }
    val filteredNames = names.filter { regex == null || it.contains(regex) }.filter { !it.endsWith("/") }
    return filteredNames.map { KafkaSchemaInfo(it) }.sortedSchemas(connectionId).take(
      limit ?: Int.MAX_VALUE) to (limit != null && names.size > limit)
  }

  fun loadSchemaInfo(schemaName: String): KafkaSchemaInfo {
    val meta = try {
      internalClient.getLatestSchemaMetadata(schemaName)
    }
    catch (t: Throwable) {
      thisLogger().info("Cannot request schemaMeta for $schemaName")
      null
    }
    meta ?: return KafkaSchemaInfo.createEmpty(schemaName)
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
    val parsedSchema = KafkaRegistryUtil.parseSchema(registryInfo.type, newText, this, registryInfo.references).getOrThrow()
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
                             schema = metadata.schema,
                             references = metadata.references)
  }

  companion object {
    fun createFor(project: Project?, connectionData: KafkaConnectionData, testConnection: Boolean) =
      createConfluentClient(connectionData, project, testConnection)

    private fun createConfluentClient(connectionData: KafkaConnectionData,
                                      project: Project?,
                                      testConnection: Boolean): ConfluentRegistryClient {
      val brokerSettings = BdtPropertyComponent.parseProperties(connectionData.secretProperties).associate {
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
      val registryProps = BdtPropertyComponent.parseProperties(connectionData.secretRegistryProperties).associate {
        (it.name ?: "") to (it.value ?: "")
      }
      val props = brokerSettings + brokerSsl + registryProps

      val url = props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG]?.ifBlank { null }
                ?: connectionData.registryUrl?.ifBlank { null }
                ?: throw BdtConfigurationException(KafkaMessagesBundle.message("error.confluent.registry.url.empty"))

      val tunnel = BdtSshTunnelService.createIfRequired(project, connectionData.getTunnelData().copy(localPort = null),
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