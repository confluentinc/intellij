package io.confluent.intellijplugin.data

import io.confluent.intellijplugin.core.monitoring.data.model.FieldsGroupModel
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import org.jetbrains.concurrency.Promise

/**
 * Interface for data managers that provide Schema Registry functionality.
 * Implemented by both KafkaDataManager (direct connection) and ClusterScopedDataManager (CCloud).
 */
interface SchemaRegistryDataProvider {
    val connectionId: String
    val registryType: KafkaRegistryType
    val schemaRegistryModel: ObjectDataModel<KafkaSchemaInfo>?
    val updater: BdtMonitoringUpdater

    fun updatePinedSchemas(schemaName: String, isForAdding: Boolean)
    fun getSchemaVersionsModel(schemaName: String): FieldsGroupModel<List<Long>>
    fun getSchemaVersionInfo(schemaName: String, version: Long): Promise<SchemaVersionInfo>
    fun deleteRegistrySchemaVersion(versionSchema: SchemaVersionInfo)
    fun updateSchema(versionInfo: SchemaVersionInfo, newText: String): Promise<Unit>
}
