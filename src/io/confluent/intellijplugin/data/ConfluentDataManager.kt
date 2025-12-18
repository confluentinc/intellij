package io.confluent.intellijplugin.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.model.CCloudEnvironment
import io.confluent.intellijplugin.ccloud.model.KafkaCluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.client.ConfluentClient
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.rfs.ConfluentConnectionData

/**
 * Data manager for Confluent Cloud resources.
 * Manages environments, clusters, and schema registries data.
 */
class ConfluentDataManager(
    project: Project?,
    override val connectionData: ConfluentConnectionData,
    override val settings: IntervalUpdateSettings,
    driverProvider: () -> MonitoringDriver
) : MonitoringDataManager(project, settings, driverProvider) {

    override val client = ConfluentClient(project, connectionData).also { Disposer.register(this, it) }

    init {
        init()
    }

    fun getEnvironments(): List<CCloudEnvironment> = client.getEnvironments()

    fun getKafkaClusters(environmentId: String): List<KafkaCluster> = client.getKafkaClusters(environmentId)

    fun getSchemaRegistries(environmentId: String): List<SchemaRegistry> = client.getSchemaRegistries(environmentId)

    fun refreshEnvironments(): List<CCloudEnvironment> = client.refreshEnvironments()

    fun refreshClusters(environmentId: String): List<KafkaCluster> = client.refreshClusters(environmentId)

    fun refreshSchemaRegistries(environmentId: String): List<SchemaRegistry> = client.refreshSchemaRegistries(environmentId)
}

