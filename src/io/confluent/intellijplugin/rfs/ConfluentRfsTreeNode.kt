package io.confluent.intellijplugin.rfs

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isCluster
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isClustersFolder
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isEnvironment
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemaRegistryFolder
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemaRegistry
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getEnvironmentId
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringRfsTreeNode
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import javax.swing.Icon

/**
 * Custom tree node for Confluent Cloud resources.
 * Provides icons and display text based on the node type:
 * - Environments
 * - Clusters folder / Schema Registry folder
 * - Individual Cluster / Schema Registry
 */
class ConfluentRfsTreeNode(
    project: Project,
    rfsPath: RfsPath,
    private val confluentDriver: ConfluentDriver
) : MonitoringRfsTreeNode(project, rfsPath, confluentDriver) {

    init {
        myName = getDisplayName()
    }

    override fun isAlwaysLeaf(): Boolean = rfsPath.isCluster || rfsPath.isSchemaRegistry

    private fun getDisplayName(): String {
        return when {
            rfsPath.isEnvironment -> {
                // Get the environment display name from client
                val envId = rfsPath.name
                confluentDriver.dataManager.client.getEnvironments()
                    .find { it.id == envId }
                    ?.displayName ?: envId
            }
            rfsPath.isClustersFolder -> ConfluentDriver.CLUSTERS_FOLDER
            rfsPath.isSchemaRegistryFolder -> ConfluentDriver.SCHEMA_REGISTRY_FOLDER
            rfsPath.isCluster -> {
                val envId = rfsPath.getEnvironmentId() ?: return rfsPath.name
                val clusterId = rfsPath.name
                confluentDriver.dataManager.client.getKafkaClusters(envId)
                    .find { it.id == clusterId }
                    ?.displayName ?: clusterId
            }
            rfsPath.isSchemaRegistry -> {
                val envId = rfsPath.getEnvironmentId() ?: return rfsPath.name
                val srId = rfsPath.name
                confluentDriver.dataManager.client.getSchemaRegistry(envId)
                    .find { it.id == srId }
                    ?.displayName ?: srId
            }
            else -> rfsPath.name
        }
    }

    override fun getIdleIcon(): Icon? = when {
        rfsPath.isEnvironment -> AllIcons.Nodes.Folder
        rfsPath.isClustersFolder -> AllIcons.Nodes.Module
        rfsPath.isSchemaRegistryFolder -> AllIcons.Nodes.DataSchema
        rfsPath.isCluster -> AllIcons.Nodes.Module
        rfsPath.isSchemaRegistry -> AllIcons.Nodes.DataSchema
        else -> null
    }

    override fun getGrayText(): String? = when {
        rfsPath.isEnvironment -> rfsPath.name // Show env ID as gray text
        rfsPath.isCluster -> {
            val envId = rfsPath.getEnvironmentId() ?: return null
            val cluster = confluentDriver.dataManager.client.getKafkaClusters(envId)
                .find { it.id == rfsPath.name }
            cluster?.let { "${it.cloudProvider} / ${it.region}" }
        }
        rfsPath.isSchemaRegistry -> {
            val envId = rfsPath.getEnvironmentId() ?: return null
            val sr = confluentDriver.dataManager.client.getSchemaRegistry(envId)
                .find { it.id == rfsPath.name }
            sr?.let { "${it.cloudProvider} / ${it.region}" }
        }
        else -> null
    }

    override fun onDoubleClick(): Boolean = when {
        rfsPath.isCluster || rfsPath.isSchemaRegistry -> true // Details shown in panel
        else -> false // Allow default expansion behavior
    }
}

