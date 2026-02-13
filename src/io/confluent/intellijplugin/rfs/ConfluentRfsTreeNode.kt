package io.confluent.intellijplugin.rfs

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isCluster
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemaRegistry
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isTopic
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchema
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getEnvironmentId
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getClusterId
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getSchemaRegistryId
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringRfsTreeNode
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import javax.swing.Icon

/**
 * Custom tree node for Confluent Cloud resources (environment-filtered flattened hierarchy).
 * With environment selector, the tree shows:
 * - Individual Clusters (depth 1)
 * - Individual Schema Registries (depth 1)
 * - Individual Topics (depth 2, under clusters)
 * - Individual Schemas (depth 2, under schema registries)
 */
class ConfluentRfsTreeNode(
    project: Project,
    rfsPath: RfsPath,
    private val confluentDriver: ConfluentDriver,
    private val schemaType: String? = null
) : MonitoringRfsTreeNode(project, rfsPath, confluentDriver) {

    override fun isAlwaysLeaf(): Boolean = rfsPath.isTopic || rfsPath.isSchema

    override fun name(): String = getDisplayName()

    private fun getDisplayName(): String {
        val envId = confluentDriver.selectedEnvironmentId ?: return rfsPath.name

        return when {
            rfsPath.isCluster(confluentDriver) -> getCluster(envId)?.displayName ?: rfsPath.name
            rfsPath.isSchemaRegistry(confluentDriver) -> "Schema Registry"
            rfsPath.isTopic || rfsPath.isSchema -> rfsPath.name
            else -> rfsPath.name
        }
    }

    override fun getIdleIcon(): Icon? {
        return when {
            rfsPath.isCluster(confluentDriver) -> AllIcons.Nodes.Module
            rfsPath.isSchemaRegistry(confluentDriver) -> AllIcons.Nodes.DataSchema
            rfsPath.isTopic -> if (checkIsFavorite()) AllIcons.Nodes.Favorite else AllIcons.Nodes.Tag
            rfsPath.isSchema -> AllIcons.FileTypes.Json
            else -> null
        }
    }

    private fun checkIsFavorite(): Boolean {
        val envId = confluentDriver.selectedEnvironmentId ?: return false
        val clusterId = rfsPath.elements.getOrNull(0) ?: return false

        val cluster = confluentDriver.dataManager.getKafkaClusters(envId)
            .find { it.id == clusterId } ?: return false

        val clusterDataManager = confluentDriver.dataManager.getOrCreateClusterDataManager(cluster)
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(clusterDataManager.connectionId)
        return config.topicsPined.contains(rfsPath.name)
    }

    override fun getGrayText(): String? {
        val envId = confluentDriver.selectedEnvironmentId ?: return null

        return when {
            rfsPath.isCluster(confluentDriver) -> {
                getCluster(envId)?.let { "${it.cloudProvider} / ${it.region}" }
            }
            rfsPath.isSchemaRegistry(confluentDriver) -> {
                getSchemaRegistry(envId)?.let { "${it.cloudProvider} / ${it.region}" }
            }
            rfsPath.isSchema -> schemaType
            else -> null
        }
    }

    override fun update(presentation: PresentationData) {
        super.update(presentation)
        presentation.tooltip = getTooltipText()
    }

    private fun getCluster(envId: String) =
        confluentDriver.dataManager.client.getKafkaClusters(envId)
            .find { it.id == rfsPath.name }

    private fun getSchemaRegistry(envId: String) =
        confluentDriver.dataManager.client.getSchemaRegistry(envId)

    private fun getTooltipText(): String? {
        val envId = confluentDriver.selectedEnvironmentId ?: return null

        return when {
            rfsPath.isCluster(confluentDriver) -> getCluster(envId)?.let { "ID: ${it.id}" }
            rfsPath.isSchemaRegistry(confluentDriver) -> getSchemaRegistry(envId)?.let { "ID: ${it.id}" }
            else -> null
        }
    }

    override fun onDoubleClick(): Boolean = when {
        rfsPath.isTopic || rfsPath.isSchema -> true
        else -> false
    }
}

