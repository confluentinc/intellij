package com.jetbrains.bigdatatools.kafka.registry.glue

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.bigdatatools.aws.connection.AwsConnectionUtils
import com.intellij.bigdatatools.aws.connection.auth.AuthenticationType
import com.intellij.bigdatatools.aws.connection.auth.AwsAuthUtil
import com.intellij.bigdatatools.aws.driver.AwsCredentialController
import com.intellij.bigdatatools.aws.ui.external.AwsSettingsInfo
import com.jetbrains.bigdatatools.common.monitoring.connection.MonitoringClient
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaDetailedInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaVersionInfo
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.*

class BdtGlueRegistryClient(project: Project?,
                            val awsSettings: AwsSettingsInfo) : MonitoringClient(project) {
  private val authentication = AwsAuthUtil.getPrimaryAuthentication(awsSettings)
  private val credentialsController = AwsCredentialController(authentication.getCredentialsProvider())

  private var client: GlueClient? = null

  override fun getRealUri(): String = "https://console.aws.amazon.com/elasticmapreduce/home?region=${awsSettings.region}"

  override fun dispose() {
    client?.close()
  }

  override fun connectInner(calledByUser: Boolean) {
    credentialsController.wrapWithAllowDialogs(calledByUser) {
      if (client == null || calledByUser) {
        client?.close()
        client = createClient()
      }
    }
  }

  override fun checkConnectionInner() {
    listSchemas(null)
  }


  fun deleteSchemaVersion(registryName: String, schemaName: String, version: Long) {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = DeleteSchemaVersionsRequest.builder().schemaId(schemaId).versions(version.toString()).build()
    client!!.deleteSchemaVersions(request)
  }

  fun deleteSchema(registryName: String, name: String) {
    val schemaId = SchemaId.builder().schemaName(name).registryName(registryName).build()
    val request = DeleteSchemaRequest.builder().schemaId(schemaId).build()
    client!!.deleteSchema(request)

  }

  fun listSchemas(registryName: String?): List<GlueSchemaInfo> {
    val registryId = registryName?.let { RegistryId.builder().registryName(registryName).build() }
    val request = ListSchemasRequest.builder().registryId(registryId).build()
    return client!!.listSchemas(request).schemas().map {
      GlueSchemaInfo(schemaName = it.schemaName() ?: "",
                     registryName = it.registryName() ?: "",
                     schemaArn = it.schemaArn() ?: "",
                     createdTime = it.createdTime() ?: "",
                     description = it.description() ?: "",
                     schemaStatus = it.schemaStatusAsString() ?: "",
                     updatedTime = it.updatedTime() ?: "")
    }
  }

  fun getDetailedSchema(registryName: String, schemaName: String): GlueSchemaDetailedInfo {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val schemaRequest = GetSchemaRequest.builder().schemaId(schemaId).build()
    val schemaResponse = client!!.getSchema(schemaRequest)

    val versionNumber = SchemaVersionNumber.builder().versionNumber(schemaResponse.latestSchemaVersion()).build()
    val versionRequest = GetSchemaVersionRequest.builder().schemaId(schemaId).schemaVersionNumber(versionNumber)
    val versionResponse = client!!.getSchemaVersion(versionRequest.build())

    val tags = client?.getTags(GetTagsRequest.builder().resourceArn(schemaResponse.schemaArn()).build())?.tags()

    return GlueSchemaDetailedInfo(
      schemaResponse = schemaResponse,
      versionResponse = versionResponse,
      tags = tags
    )
  }

  fun getSchema(registryName: String, schemaName: String): GetSchemaResponse {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = GetSchemaRequest.builder().schemaId(schemaId).build()
    return client!!.getSchema(request)
  }

  fun getSchemaVersion(registryName: String, schemaName: String, version: Long): GetSchemaVersionResponse {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val versionNumber = SchemaVersionNumber.builder().versionNumber(version).build()
    val request = GetSchemaVersionRequest.builder().schemaId(schemaId).schemaVersionNumber(versionNumber).build()
    val response = client!!.getSchemaVersion(request)
    return response
  }


  fun listSchemaVersions(registryName: String, schemaName: String): List<GlueSchemaVersionInfo> {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    val request = ListSchemaVersionsRequest.builder().schemaId(schemaId).build()
    return client!!.listSchemaVersions(request).schemas().map {
      GlueSchemaVersionInfo(version = it.versionNumber() ?: -1, registered = it.createdTime() ?: "", status = it.statusAsString() ?: "",
                            schemaId = schemaId)
    }
  }

  fun createSchema(registryName: String,
                   schemaName: String,
                   dataFormat: DataFormat,
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
    client!!.createSchema(request)
  }

  fun registerNewSchemaVersion(schemaId: SchemaId, newSchemaDefinition: @NlsSafe String) {
    val request = RegisterSchemaVersionRequest.builder()
      .schemaId(schemaId)
      .schemaDefinition(newSchemaDefinition)
      .build()
    client!!.registerSchemaVersion(request)
  }

  private fun createClient(): GlueClient? {
    val httpClient = AwsConnectionUtils.createHttpClient(null, false)
    val overrideConfiguration = if (AuthenticationType.getById(awsSettings.authenticationType) in setOf(AuthenticationType.KEY_PAIR,
                                                                                                        AuthenticationType.ANON,
                                                                                                        AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE)) {
      val clientConfiguration = ClientOverrideConfiguration.builder()
      clientConfiguration.defaultProfileFile(ProfileFile.aggregator().build()).build()
    }
    else {
      null
    }

    val clientBuilder = GlueClient.builder()
      .credentialsProvider(credentialsController.credentials)
      .region(Region.of(awsSettings.region))
      .httpClient(httpClient)
    if (overrideConfiguration != null)
      clientBuilder.overrideConfiguration(overrideConfiguration)
    return clientBuilder.build()
  }
}