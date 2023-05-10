package com.jetbrains.bigdatatools.kafka.registry.confluent

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.common.connection.exception.BdtConnectionException
import com.jetbrains.bigdatatools.common.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.storage.ObjectDataModelStorage
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.runAsync
import com.jetbrains.bigdatatools.common.util.withCatchNotifyErrorDialog
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema

class ConfluentRegistryDataManager(val dataManager: MonitoringDataManager,
                                   val clientRetriever: () -> ConfluentRegistryClient?) : Disposable {
  val client: ConfluentRegistryClient
    get() = clientRetriever() ?: throw BdtConnectionException(KafkaMessagesBundle.message("error.confluent.client.is.not.inited"))

  internal val schemaRegistryModel = createSchemaRegistryDataModel().also {
    Disposer.register(this, it)
  }

  private val schemaVersionsModels = createSchemeVersionsStorage().also { Disposer.register(this, it) }
  private val schemaFieldsModels = createSchemaFieldsStorage().also { Disposer.register(this, it) }


  override fun dispose() {}

  private fun createSchemaRegistryDataModel(): ObjectDataModel<ConfluentSchemaInfo> {
    val dataModel = ObjectDataModel(ConfluentSchemaInfo::id) {
      loadSchemaInfos()
    }
    return dataModel
  }

  private fun loadSchemaInfos(): List<ConfluentSchemaInfo> {
    val subjects = loadSchemaNames()

    return subjects.map {
      val meta = try {
        client.getLatestSchemaMetadata(it)
      }
      catch (t: Throwable) {
        null
      }
      ConfluentSchemaInfo(name = it, meta = meta)
    }
  }

  private fun loadSchemaNames() = client.getAllSubjects(KafkaToolWindowSettings.getInstance().registryShowDeletedSubjects)

  fun getRegistrySchemaFieldsModel(id: Int): ObjectDataModel<SchemaRegistryFieldsInfo> = schemaFieldsModels[id]
  fun getRegistrySchemaVersionsModel(id: Int): ObjectDataModel<ConfluentSchemaInfo> = schemaVersionsModels[id]
  fun getSchemaInfo(id: Int) = schemaRegistryModel.data?.firstOrNull { it.id == id }


  fun deleteRegistrySchemaVersion(registryInfo: ConfluentSchemaInfo, isPermanent: Boolean = false) = executeOnPooledThread {
    withCatchNotifyErrorDialog {
      val name = registryInfo.name
      client.deleteSchemaVersion(name, registryInfo.versions.toString(), isPermanent)

      dataManager.updater.invokeRefreshModel(schemaRegistryModel)
      dataManager.updater.invokeRefreshModel(getRegistrySchemaVersionsModel(registryInfo.id))
    }
  }

  private fun deleteRegistrySchema(name: String, isPermanent: Boolean) = executeOnPooledThread {
    withCatchNotifyErrorDialog {
      client.deleteSubject(name, isPermanent)
      dataManager.updater.invokeRefreshModel(schemaRegistryModel)
    }
  }

  fun deleteRegistrySchema(registryInfo: ConfluentSchemaInfo, isPermanent: Boolean) = executeOnPooledThread {
    deleteRegistrySchema(registryInfo.name, isPermanent)
  }

  fun createRegistrySubject(schemaName: String, parsedSchema: ParsedSchema) = runAsync {
    client.register(schemaName, parsedSchema)
    dataManager.updater.invokeRefreshModel(schemaRegistryModel)
  }

  fun updateSchema(registryInfo: ConfluentSchemaInfo, newText: @NlsSafe String) = runAsync {
    val parsedSchema = KafkaRegistryUtil.parseSchema(registryInfo, newText)
    client.register(registryInfo.name, parsedSchema)

    dataManager.updater.invokeRefreshModel(schemaRegistryModel)
    dataManager.updater.invokeRefreshModel(getRegistrySchemaVersionsModel(registryInfo.id))
  }

  fun getRegistrySchema(subjectName: String) = schemaRegistryModel.data?.firstOrNull { it.name == subjectName }

  private fun createSchemeVersionsStorage() = ObjectDataModelStorage<Int, ConfluentSchemaInfo>(dataManager.updater,
                                                                                               ConfluentSchemaInfo::id) {
    val client = client
    val meta = getSchemaInfo(it) ?: error("Meta is not found")

    val versions = client.getAllVersions(meta.name)
    versions.map { version ->
      val versionMeta = client.getSchemaMetadata(meta.name, version)
      ConfluentSchemaInfo(name = meta.name, meta = versionMeta)
    }
  }


  private fun createSchemaFieldsStorage() = ObjectDataModelStorage<Int, SchemaRegistryFieldsInfo>(dataManager.updater,
                                                                                                  SchemaRegistryFieldsInfo::name) {
    val schema = this.client.getSchemaById(it) ?: error("Meta is not found")
    KafkaRegistryUtil.parseFields(schema)
  }

  companion object {
    fun getInstance(connectionId: String,
                    project: Project) = (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}