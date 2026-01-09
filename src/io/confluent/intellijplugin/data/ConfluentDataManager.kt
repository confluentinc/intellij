package io.confluent.intellijplugin.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.client.ControlPlaneCache
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.rfs.ConfluentConnectionData

/**
 * Data manager for Confluent Cloud resources.
 * Provides the ControlPlaneCache for accessing Confluent Cloud control plane resources.
 *
 * Access organizational resources directly via the client:
 * - client.getEnvironments()
 * - client.getKafkaClusters(environmentId)
 * - client.getSchemaRegistry(environmentId)
 */
class ConfluentDataManager(
    project: Project?,
    override val connectionData: ConfluentConnectionData,
    override val settings: IntervalUpdateSettings,
    driverProvider: () -> MonitoringDriver
) : MonitoringDataManager(project, settings, driverProvider) {

    override val client = ControlPlaneCache(project, connectionData).also { Disposer.register(this, it) }

    init {
        init()
    }
}

