package io.confluent.intellijplugin.rfs

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowController
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.tree.DriverRfsTreeModel
import io.confluent.intellijplugin.core.rfs.tree.node.RfsDriverTreeNodeBuilder
import io.confluent.intellijplugin.data.ConfluentDataManager
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import javax.swing.Icon

/**
 * Driver for Confluent Cloud resources.
 * Provides a 4-level hierarchical tree structure:
 * - Root
 *   - Environments
 *     - Clusters folder / Schema Registry folder
 *       - Individual Cluster / Schema Registry
 */
class ConfluentDriver(
    override val connectionData: ConfluentConnectionData,
    project: Project?,
    testConnection: Boolean
) : MonitoringDriver(project, testConnection) {

    override val dataManager: ConfluentDataManager = ConfluentDataManager(
        project, connectionData, KafkaToolWindowSettings.getInstance(), { this }
    )

    override val presentableName: String = connectionData.name
    override val icon: Icon = AllIcons.Nodes.Folder

    override val treeNodeBuilder: RfsDriverTreeNodeBuilder = object : RfsDriverTreeNodeBuilder() {
        override fun createNode(project: Project, path: RfsPath, driver: Driver) =
            ConfluentRfsTreeNode(project, path, this@ConfluentDriver)
    }

    init {
        Disposer.register(this, dataManager)
    }

    override fun getController(project: Project): MonitoringToolWindowController? {
        // Return null for now, can be extended later if needed
        return null
    }

    override fun dispose() {}

    override fun createTreeModel(rootPath: RfsPath, project: Project) =
        DriverRfsTreeModel(project, rootPath, this, false)

    override fun doLoadFileInfo(rfsPath: RfsPath): FileInfo = ConfluentFileInfo(this, rfsPath)

    override fun doLoadChildren(rfsPath: RfsPath): List<FileInfo>? {
        dataManager.client.connectionError?.let { throw it }

        val depth = rfsPath.elements.size

        return when (depth) {
            // Root level: show environments
            0 -> {
                dataManager.client.getEnvironments().map { env ->
                    ConfluentFileInfo(this, environmentPath(env.id))
                }
            }
            // Environment level: show Clusters and Schema Registry folders
            1 -> {
                val envId = rfsPath.name
                listOf(
                    ConfluentFileInfo(this, clustersPath(envId)),
                    ConfluentFileInfo(this, schemaRegistryFolderPath(envId))
                )
            }
            // Clusters folder: show individual clusters
            2 -> {
                if (rfsPath.name == CLUSTERS_FOLDER) {
                    val envId = rfsPath.parent?.name ?: return emptyList()
                    dataManager.client.getKafkaClusters(envId).map { cluster ->
                        ConfluentFileInfo(this, clusterPath(envId, cluster.id))
                    }
                } else if (rfsPath.name == SCHEMA_REGISTRY_FOLDER) {
                    val envId = rfsPath.parent?.name ?: return emptyList()
                    dataManager.client.getSchemaRegistry(envId).map { sr ->
                        ConfluentFileInfo(this, schemaRegistryPath(envId, sr.id))
                    }
                } else {
                    emptyList()
                }
            }
            // Cluster/SR level: leaf nodes (for now, can be extended for Topics, Consumer Groups, etc.)
            else -> emptyList()
        }
    }

    // Path utilities for 4-level hierarchy
    private fun environmentPath(envId: String) = RfsPath(listOf(envId), true)
    private fun clustersPath(envId: String) = RfsPath(listOf(envId, CLUSTERS_FOLDER), true)
    private fun schemaRegistryFolderPath(envId: String) = RfsPath(listOf(envId, SCHEMA_REGISTRY_FOLDER), true)
    private fun clusterPath(envId: String, clusterId: String) = RfsPath(listOf(envId, CLUSTERS_FOLDER, clusterId), false)
    private fun schemaRegistryPath(envId: String, srId: String) = RfsPath(listOf(envId, SCHEMA_REGISTRY_FOLDER, srId), false)

    companion object {
        const val CLUSTERS_FOLDER = "Clusters"
        const val SCHEMA_REGISTRY_FOLDER = "Schema Registry"

        // Path type checks using size instead of depth
        val RfsPath.isEnvironment: Boolean get() = elements.size == 1
        val RfsPath.isClustersFolder: Boolean get() = elements.size == 2 && name == CLUSTERS_FOLDER
        val RfsPath.isSchemaRegistryFolder: Boolean get() = elements.size == 2 && name == SCHEMA_REGISTRY_FOLDER
        val RfsPath.isCluster: Boolean get() = elements.size == 3 && parent?.name == CLUSTERS_FOLDER
        val RfsPath.isSchemaRegistry: Boolean get() = elements.size == 3 && parent?.name == SCHEMA_REGISTRY_FOLDER

        // Get environment ID from any path
        fun RfsPath.getEnvironmentId(): String? = elements.firstOrNull()
    }
}

