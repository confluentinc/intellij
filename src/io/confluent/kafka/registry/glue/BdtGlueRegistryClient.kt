package io.confluent.kafka.registry.glue

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import io.confluent.kafka.aws.connection.AwsConnectionUtils
import io.confluent.kafka.aws.connection.auth.AwsAuthUtil
import io.confluent.kafka.aws.driver.AwsCredentialController
import io.confluent.kafka.aws.ui.external.AwsSettingsInfo
import io.confluent.kafka.core.util.TimeUtils
import io.confluent.kafka.data.KafkaDataManager.Companion.sortedSchemas
import io.confluent.kafka.registry.KafkaRegistryFormat
import io.confluent.kafka.registry.SchemaVersionInfo
import io.confluent.kafka.registry.common.KafkaSchemaInfo
import kotlinx.coroutines.delay
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.*
import java.util.*

class BdtGlueRegistryClient(val project: Project?,
                            private val registryName: String,
                            private val awsSettings: AwsSettingsInfo) : Disposable {
  val region: String? = awsSettings.region
  private val authentication = AwsAuthUtil.getPrimaryAuthentication(awsSettings)
  val credentialsController = AwsCredentialController(authentication.getCredentialsProvider())

  lateinit var client: GlueClient

  override fun dispose() {
    try {
      if (::client.isInitialized) {
        client.close()
      }
    }
    catch (_: Throwable) {
      //ignore
    }
  }

  fun connect(calledByUser: Boolean) {
    credentialsController.wrapWithAllowDialogs(calledByUser) {
      client = createClient()
      if (calledByUser) {
        if (registryName.isBlank())
          listRegistries()
        else
          listSchemas(1, null)
      }
    }
  }

  fun checkConnection() {
    listSchemas(1, null)
  }

  fun deleteSchemaVersion(schemaVersionInfo: SchemaVersionInfo) {
    val schemaId = SchemaId.builder().schemaName(schemaVersionInfo.schemaName).registryName(registryName).build()
    val request = DeleteSchemaVersionsRequest.builder().schemaId(schemaId).versions(schemaVersionInfo.version.toString()).build()
    client.deleteSchemaVersions(request)
  }

  suspend fun deleteSchema(name: String) {
    val schemaId = SchemaId.builder().schemaName(name).registryName(registryName).build()
    val request = DeleteSchemaRequest.builder().schemaId(schemaId).build()
    client.deleteSchema(request)
    delay(2000)
  }

  fun listRegistries(): List<RegistryListItem> {
    val request = ListRegistriesRequest.builder().build()
    return client.listRegistries(request).registries()
  }

  fun listSchemas(size: Int? = null, filter: String?, connectionId: String? = null): Pair<List<KafkaSchemaInfo>, Boolean> {
    val registryId = registryName.let { RegistryId.builder().registryName(registryName).build() }
    val requestBuilder = ListSchemasRequest.builder().registryId(registryId)
    if (size != null) {
      requestBuilder.maxResults(size)
    }
    var request = requestBuilder.build()

    var left = size
    val filterRegex = filter?.let { Regex(it) }
    val totalResult = mutableListOf<KafkaSchemaInfo>()
    while (true) {
      if (left == 0)
        return totalResult to false

      val response = client.listSchemas(request)
      val schemas = response.schemas()
        .filter { filterRegex == null || it.schemaName().contains(filterRegex) }
        .filter { !it.schemaName().endsWith("/") }
        .map {
          val schemaName = it.schemaName()
          KafkaSchemaInfo(name = schemaName ?: "",
                          type = null,
                          compatibility = null,
                          version = null,
                          description = it.description() ?: "",
                          schemaStatus = it.schemaStatusAsString() ?: "",
                          updatedTime = TimeUtils.parseIsoTime(it.updatedTime()))
        }
      val sortedSchemas = if (connectionId != null) schemas.sortedSchemas(connectionId) else schemas
      if (left == null) {
        totalResult.addAll(sortedSchemas)
      }
      if (left != null && sortedSchemas.size >= left) {
        totalResult.addAll(sortedSchemas.subList(0, left))
        return totalResult to true
      }
      if (left != null) {
        totalResult.addAll(sortedSchemas)
        left -= sortedSchemas.size
      }
      val nextMarker = response.nextToken()
      if (nextMarker == null)
        return totalResult to false
      request = request.toBuilder().nextToken(nextMarker).build()
    }
  }

  fun getLatestVersionId(schemaName: String): UUID {
    val schemaVersion = getSchemaResponse(schemaName).latestSchemaVersion()
    val versionResponse = getVersionResponse(schemaName, schemaVersion)
    return UUID.fromString(versionResponse.schemaVersionId())
  }

  private fun getSchemaResponse(schemaName: String): GetSchemaResponse {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val schemaRequest = GetSchemaRequest.builder().schemaId(schemaId).build()
    return client.getSchema(schemaRequest)
  }

  private fun getVersionResponse(schemaName: String, version: Long): GetSchemaVersionResponse {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val versionNumber = SchemaVersionNumber.builder().versionNumber(version).build()
    val versionRequest = GetSchemaVersionRequest.builder().schemaId(schemaId).schemaVersionNumber(versionNumber)
    return client.getSchemaVersion(versionRequest.build())
  }

  private fun loadSchema(schemaName: String): GetSchemaResponse {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = GetSchemaRequest.builder().schemaId(schemaId).build()
    return client.getSchema(request)
  }

  fun getSchemaVersionInfo(schemaName: String, version: Long): SchemaVersionInfo {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val versionNumber = SchemaVersionNumber.builder().versionNumber(version).build()
    val request = GetSchemaVersionRequest.builder().schemaId(schemaId).schemaVersionNumber(versionNumber).build()
    val response = client.getSchemaVersion(request)
    val registryFormat = response.dataFormat().toRegistryFormat()
    return SchemaVersionInfo(schemaName, version, registryFormat, response.schemaDefinition())
  }


  fun listSchemaVersions(schemaName: String): List<Long> {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = ListSchemaVersionsRequest.builder().schemaId(schemaId).build()
    return client.listSchemaVersions(request).schemas().map {
      it.versionNumber()
    }
  }

  fun createSchema(schemaName: String,
                   dataFormat: String,
                   schemaDefinition: String,
                   compatibility: Compatibility,
                   description: String,
                   tags: Map<String, String>) {
    val request = CreateSchemaRequest.builder()
      .schemaDefinition(schemaDefinition)
      .dataFormat(dataFormat)
      .schemaName(schemaName)
      .registryId(RegistryId.builder().registryName(registryName).build())
      .compatibility(compatibility)
      .description(description)
      .tags(tags)
      .build()
    client.createSchema(request)
  }

  fun updateSchema(schemaId: SchemaVersionInfo, newSchemaDefinition: @NlsSafe String) {
    val request = RegisterSchemaVersionRequest.builder()
      .schemaId(SchemaId.builder().schemaName(schemaId.schemaName).registryName(registryName).build())
      .schemaDefinition(newSchemaDefinition)
      .build()
    client.registerSchemaVersion(request)
  }

  private fun createClient(): GlueClient {
    val httpClient = AwsConnectionUtils.createHttpClient(null, false)
    val clientConfiguration = ClientOverrideConfiguration.builder()
    val overrideConfiguration = clientConfiguration.defaultProfileFile(ProfileFile.aggregator().build()).build()

    val clientBuilder = GlueClient.builder()
      .credentialsProvider(credentialsController.credentials)
      .region(Region.of(awsSettings.region))
      .httpClient(httpClient)
    if (overrideConfiguration != null)
      clientBuilder.overrideConfiguration(overrideConfiguration)
    return clientBuilder.build()
  }

  fun loadSchemaInfo(schemaName: String): KafkaSchemaInfo {
    val response = loadSchema(schemaName)
    return KafkaSchemaInfo(
      name = schemaName,
      version = response.latestSchemaVersion(),
      type = KafkaRegistryFormat.parse(response.dataFormatAsString()),
      compatibility = response.compatibilityAsString(),
      description = response.description() ?: "",
      schemaStatus = response.schemaStatusAsString() ?: "",
      updatedTime = TimeUtils.parseIsoTime(response.updatedTime()),
    )
  }

  fun getLatestVersionInfo(schemaName: String): SchemaVersionInfo {
    val schemaResponse = getSchemaResponse(schemaName)
    val schemaVersion = schemaResponse.latestSchemaVersion()
    val versionResponse = getVersionResponse(schemaName, schemaVersion)
    return SchemaVersionInfo(schemaName = schemaName, version = schemaVersion,
                             type = KafkaRegistryFormat.parse(schemaResponse.dataFormatAsString()),
                             schema = versionResponse.schemaDefinition())
  }

  companion object {
    fun DataFormat.toRegistryFormat() = when (this) {
      DataFormat.AVRO -> KafkaRegistryFormat.AVRO
      DataFormat.JSON -> KafkaRegistryFormat.JSON
      DataFormat.PROTOBUF -> KafkaRegistryFormat.PROTOBUF
      DataFormat.UNKNOWN_TO_SDK_VERSION -> error("Wrong format")
    }
  }
}