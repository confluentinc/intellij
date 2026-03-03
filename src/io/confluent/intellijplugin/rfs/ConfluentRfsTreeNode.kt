package io.confluent.intellijplugin.rfs

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
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
import io.confluent.intellijplugin.util.KafkaMessagesBundle.message
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
    private val confluentDriver: ConfluentDriver
) : MonitoringRfsTreeNode(project, rfsPath, confluentDriver) {

    override fun isAlwaysLeaf(): Boolean = rfsPath.isTopic || rfsPath.isSchema

    override fun name(): String {
        val envId = confluentDriver.selectedEnvironmentId ?: return rfsPath.name

        return when {
            rfsPath.isCluster(confluentDriver) -> {
                confluentDriver.dataManager.client.getKafkaClusters(envId)
                    .find { it.id == rfsPath.name }
                    ?.displayName ?: rfsPath.name
            }
            rfsPath.isSchemaRegistry(confluentDriver) -> "Schema Registry"
            rfsPath.isTopic || rfsPath.isSchema -> rfsPath.name
            else -> rfsPath.name
        }
    }

    override fun getIdleIcon(): Icon? {
        return when {
            rfsPath.isCluster(confluentDriver) -> BigdatatoolsKafkaIcons.ConfluentKafkaCluster
            rfsPath.isSchemaRegistry(confluentDriver) -> BigdatatoolsKafkaIcons.ConfluentSrCluster
            rfsPath.isTopic -> if (checkIsTopicFavorite()) AllIcons.Nodes.Favorite else BigdatatoolsKafkaIcons.ConfluentTopic
            rfsPath.isSchema -> if (checkIsSchemaFavorite()) AllIcons.Nodes.Favorite else BigdatatoolsKafkaIcons.ConfluentSchema
            else -> null
        }
    }

    private fun checkIsTopicFavorite(): Boolean {
        val envId = confluentDriver.selectedEnvironmentId ?: return false
        val clusterId = rfsPath.elements.getOrNull(0) ?: return false

        val cluster = confluentDriver.dataManager.getKafkaClusters(envId)
            .find { it.id == clusterId } ?: return false

        val clusterDataManager = confluentDriver.dataManager.getOrCreateClusterDataManager(cluster)
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(clusterDataManager.connectionId)
        return config.topicsPined.contains(rfsPath.name)
    }

    private fun checkIsSchemaFavorite(): Boolean {
        val envId = confluentDriver.selectedEnvironmentId ?: return false

        // Get SR ID from path: [srId, schemaName]
        val srId = rfsPath.elements.getOrNull(0) ?: return false

        // Verify this SR exists in the current environment
        val schemaRegistry = confluentDriver.dataManager.client.getSchemaRegistry(envId) ?: return false
        if (schemaRegistry.id != srId) return false

        // Use SR ID as config key (not cluster ID) so all clusters sharing this SR see the same favorites
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(srId)
        return config.schemasPined.contains(rfsPath.name)
    }

    override fun getGrayText(): String? {
        val envId = confluentDriver.selectedEnvironmentId ?: return null

        return when {
            rfsPath.isCluster(confluentDriver) -> {
                confluentDriver.dataManager.client.getKafkaClusters(envId)
                    .find { it.id == rfsPath.name }
                    ?.let { "${it.cloudProvider} / ${it.region}" }
            }
            rfsPath.isSchemaRegistry(confluentDriver) -> {
                confluentDriver.dataManager.client.getSchemaRegistry(envId)
                    ?.let { "${it.cloudProvider} / ${it.region}" }
            }
            rfsPath.isSchema -> {
                val srId = rfsPath.elements.getOrNull(0) ?: return null
                val schemaName = rfsPath.name

                val sr = confluentDriver.dataManager.client.getSchemaRegistry(envId)
                if (sr == null || sr.id != srId) return null

                val cluster = confluentDriver.dataManager.getKafkaClusters(envId).firstOrNull() ?: return null
                val clusterDataManager = confluentDriver.dataManager.getOrCreateClusterDataManager(cluster)

                clusterDataManager.getCachedSchema(schemaName)?.type?.presentable
            }
            else -> null
        }
    }

    override fun update(presentation: PresentationData) {
        super.update(presentation)

        val envId = confluentDriver.selectedEnvironmentId ?: return

        presentation.tooltip = when {
            rfsPath.isCluster(confluentDriver) -> {
                confluentDriver.dataManager.client.getKafkaClusters(envId)
                    .find { it.id == rfsPath.name }
                    ?.let { "ID: ${it.id}" }
            }
            rfsPath.isSchemaRegistry(confluentDriver) -> {
                confluentDriver.dataManager.client.getSchemaRegistry(envId)
                    ?.let { "ID: ${it.id}" }
            }
            else -> null
        }
    }

    override fun onDoubleClick(): Boolean {
        // Navigation handled by tree selection listener, not double-click
        // This matches Kafka tab behavior where single-click selection navigates
        return false
    }
}

