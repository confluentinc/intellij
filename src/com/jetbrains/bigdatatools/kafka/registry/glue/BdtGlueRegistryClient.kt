package com.jetbrains.bigdatatools.kafka.registry.glue

import com.intellij.bigdatatools.aws.connection.AwsConnectionUtils
import com.intellij.bigdatatools.aws.connection.auth.AwsAuthUtil
import com.intellij.bigdatatools.aws.driver.AwsCredentialController
import com.intellij.bigdatatools.aws.ui.external.AwsSettingsInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.common.util.TimeUtils
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaDetailedInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaVersionInfo
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.*

class BdtGlueRegistryClient(val project: Project?,
                            val registryName: String,
                            val awsSettings: AwsSettingsInfo) : Disposable {
  private val authentication = AwsAuthUtil.getPrimaryAuthentication(awsSettings)
  val credentialsController = AwsCredentialController(authentication.getCredentialsProvider())

  lateinit var client: GlueClient


  override fun dispose() {
    client.close()
  }

  fun connect(calledByUser: Boolean) {
    credentialsController.wrapWithAllowDialogs(calledByUser) {
      client = createClient()
      if (calledByUser) {
        if (registryName.isBlank())
          listRegistries()
        else
          listSchemas()
      }
    }
  }

  fun checkConnection() {
    listSchemas()
  }

  fun deleteSchemaVersion(schemaName: String, version: Long) {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = DeleteSchemaVersionsRequest.builder().schemaId(schemaId).versions(version.toString()).build()
    client.deleteSchemaVersions(request)
  }

  fun deleteSchema(name: String) {
    val schemaId = SchemaId.builder().schemaName(name).registryName(registryName).build()
    val request = DeleteSchemaRequest.builder().schemaId(schemaId).build()
    client.deleteSchema(request)

  }

  fun listRegistries(): List<RegistryListItem> {
    val request = ListRegistriesRequest.builder().build()
    return client.listRegistries(request).registries()
  }

  fun listSchemas(): List<GlueSchemaInfo> {
    val registryId = registryName.let { RegistryId.builder().registryName(registryName).build() }
    val request = ListSchemasRequest.builder().registryId(registryId).build()

    return client.listSchemas(request).schemas().map {
      val schemaName = it.schemaName()
      GlueSchemaInfo(schemaName = schemaName ?: "",
                     registryName = it.registryName() ?: "",
                     type = null,
                     compatibility = null,
                     versions = null,
                     schemaArn = it.schemaArn() ?: "",
                     createdTime = TimeUtils.parseIsoTime(it.createdTime()),
                     description = it.description() ?: "",
                     schemaStatus = it.schemaStatusAsString() ?: "",
                     updatedTime = TimeUtils.parseIsoTime(it.updatedTime()))
    }
  }

  fun getDetailedSchema(schemaName: String): GlueSchemaDetailedInfo {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val schemaRequest = GetSchemaRequest.builder().schemaId(schemaId).build()
    val schemaResponse = client.getSchema(schemaRequest)
    val versionNumber = SchemaVersionNumber.builder().versionNumber(schemaResponse.latestSchemaVersion()).build()
    val versionRequest = GetSchemaVersionRequest.builder().schemaId(schemaId).schemaVersionNumber(versionNumber)
    val versionResponse = client.getSchemaVersion(versionRequest.build())

    val tags = client.getTags(GetTagsRequest.builder().resourceArn(schemaResponse.schemaArn()).build())?.tags()

    return GlueSchemaDetailedInfo(
      schemaResponse = schemaResponse,
      versionResponse = versionResponse,
      tags = tags
    )
  }

  fun getSchema(schemaName: String): GetSchemaResponse {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = GetSchemaRequest.builder().schemaId(schemaId).build()
    return client.getSchema(request)
  }

  fun getSchemaVersion(schemaName: String, version: Long): GetSchemaVersionResponse {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val versionNumber = SchemaVersionNumber.builder().versionNumber(version).build()
    val request = GetSchemaVersionRequest.builder().schemaId(schemaId).schemaVersionNumber(versionNumber).build()
    return client.getSchemaVersion(request)
  }


  fun listSchemaVersions(schemaName: String): List<GlueSchemaVersionInfo> {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = ListSchemaVersionsRequest.builder().schemaId(schemaId).build()
    return client.listSchemaVersions(request).schemas().map {
      GlueSchemaVersionInfo(version = it.versionNumber() ?: -1, registered = it.createdTime() ?: "", status = it.statusAsString() ?: "",
                            schemaId = schemaId)
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

  fun registerNewSchemaVersion(schemaId: SchemaId, newSchemaDefinition: @NlsSafe String) {
    val request = RegisterSchemaVersionRequest.builder()
      .schemaId(schemaId)
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

  fun updateListSchemasWithLongLoadFields(original: List<GlueSchemaInfo>): List<GlueSchemaInfo> {
    return original.map {
      val response = getSchema(it.schemaName)
      it.copy(compatibility = response.compatibilityAsString(), versions = response.latestSchemaVersion(),
              type = response.dataFormatAsString())
    }
  }
}