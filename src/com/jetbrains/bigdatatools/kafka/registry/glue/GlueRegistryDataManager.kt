package com.jetbrains.bigdatatools.kafka.registry.glue

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.bigdatatools.common.connection.exception.BdtConnectionException
import com.jetbrains.bigdatatools.common.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.common.monitoring.data.model.FieldGroupsData
import com.jetbrains.bigdatatools.common.monitoring.data.model.FieldsDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.model.FieldsGroupModel
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.storage.FieldGroupsDataModelStorage
import com.jetbrains.bigdatatools.common.monitoring.data.storage.ObjectDataModelStorage
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.runAsync
import com.jetbrains.bigdatatools.common.util.withCatchNotifyErrorDialog
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaDetailedInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.SchemaVersionId
import com.jetbrains.bigdatatools.kafka.registry.glue.ui.GlueTransforms
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.services.glue.model.Compatibility
import software.amazon.awssdk.services.glue.model.DataFormat
import software.amazon.awssdk.services.glue.model.GetSchemaVersionResponse
import software.amazon.awssdk.services.glue.model.SchemaId

class GlueRegistryDataManager(val dataManager: MonitoringDataManager,
                              val clientRetriever: () -> BdtGlueRegistryClient?) : Disposable {
  val client: BdtGlueRegistryClient
    get() = clientRetriever() ?: throw BdtConnectionException(KafkaMessagesBundle.message("error.glue.client.is.not.inited"))

  val region: String
    get() = client.awsSettings.region ?: ""

  internal val schemaModel = createSchemaRegistryDataModel().also {
    Disposer.register(this, it)
  }

  private val schemaDetailsModels = createSchemaDetailedInfos().also { Disposer.register(this, it) }
  private val schemaVersionsModels = createSchemeVersionsStorage().also { Disposer.register(this, it) }
  private val schemaFieldsModels = createSchemaFieldsStorage().also { Disposer.register(this, it) }


  override fun dispose() {}

  private fun createSchemaRegistryDataModel(): ObjectDataModel<GlueSchemaInfo> {
    val dataModel = ObjectDataModel(GlueSchemaInfo::id) {
      val client = client
      client.listSchemas()
    }
    return dataModel
  }


  fun getRegistrySchemaFieldsModel(id: SchemaVersionId): ObjectDataModel<SchemaRegistryFieldsInfo> = schemaFieldsModels[id]
  fun getRegistrySchemaVersionsModel(id: SchemaId): ObjectDataModel<GlueSchemaVersionInfo> = schemaVersionsModels[id]
  fun getRegistrySchemaInfoModel(id: SchemaId): FieldsGroupModel<GlueSchemaDetailedInfo> = schemaDetailsModels[id]

  fun getDetailedSchema(id: SchemaId) = schemaDetailsModels[id].originObject

  fun deleteSchemaVersion(schemaVersionInfo: GlueSchemaVersionInfo) = executeOnPooledThread {
    withCatchNotifyErrorDialog {
      client.deleteSchemaVersion(schemaName = schemaVersionInfo.schemaId.schemaName(),
                                 version = schemaVersionInfo.version)

      dataManager.updater.invokeRefreshModel(schemaModel)
      dataManager.updater.invokeRefreshModel(getRegistrySchemaVersionsModel(schemaVersionInfo.schemaId))
    }
  }

  fun deleteSchema(schemaName: String) = executeOnPooledThread {
    withCatchNotifyErrorDialog {
      client.deleteSchema(schemaName)
      dataManager.updater.invokeRefreshModel(schemaModel)
    }
  }

  fun createSchema(schemaName: String,
                   dataFormat: DataFormat,
                   schemaDefinition: String,
                   compatibility: Compatibility,
                   description: String,
                   tags: Map<String, String>) = runAsync {
    client.createSchema(schemaName, dataFormat, schemaDefinition, compatibility, description, tags)
    dataManager.updater.invokeRefreshModel(schemaModel)
  }

  fun registerNewSchemaVersion(registryName: String, schemaName: String, newSchemaDefinition: @NlsSafe String) = runAsync {
    val schemaId = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()
    client.registerNewSchemaVersion(schemaId, newSchemaDefinition)

    dataManager.updater.invokeRefreshModel(schemaModel)
    dataManager.updater.invokeRefreshModel(getRegistrySchemaVersionsModel(schemaId))
  }


  private fun createSchemeVersionsStorage() = ObjectDataModelStorage<SchemaId, GlueSchemaVersionInfo>(dataManager.updater,
                                                                                                      GlueSchemaVersionInfo::version) {
    client.listSchemaVersions(schemaName = it.schemaName())
  }


  private fun createSchemaFieldsStorage() = ObjectDataModelStorage<SchemaVersionId, SchemaRegistryFieldsInfo>(dataManager.updater,
                                                                                                              SchemaRegistryFieldsInfo::name) {

    val detailedInfo = loadSchemaVersion(it.schemaId.schemaName(), it.versionId)

    val schema = try {
      KafkaRegistryUtil.parseSchema(schemaType = detailedInfo.dataFormatAsString(),
                                    newText = detailedInfo.schemaDefinition() ?: "").getOrThrow()
    }
    catch (t: Throwable) {
      null
    }

    KafkaRegistryUtil.parseFields(schema)
  }

  private fun createSchemaDetailedInfos() = FieldGroupsDataModelStorage<SchemaId, GlueSchemaDetailedInfo>(dataManager.updater) { id ->
    val newValue = loadDetailedSchemaInfo(id.schemaName())
    val list: List<Pair<String, String>> = newValue.tags?.entries?.map { it.key to it.value } ?: emptyList()
    val groups = listOf(
      KafkaMessagesBundle.message("datamanager.schema.details") to GlueTransforms.getSchemaInfoDetails(newValue.schemaResponse),
      (KafkaMessagesBundle.message("datamanager.schema.details.tags") to FieldsDataModel.createForList(list)
      ))
    FieldGroupsData(newValue, groups)
  }

  @RequiresBackgroundThread
  fun loadSchemaVersion(schemaName: String, version: Long): GetSchemaVersionResponse {
    return client.getSchemaVersion(schemaName, version)
  }


  @RequiresBackgroundThread
  fun loadDetailedSchemaInfo(schemaName: String): GlueSchemaDetailedInfo = client.getDetailedSchema(schemaName)


  companion object {
    fun getInstance(connectionId: String, project: Project) = (DriverManager.getDriverById(project,
                                                                                           connectionId) as? KafkaDriver)?.dataManager
  }
}