package io.confluent.intellijplugin.rfs

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
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
import io.confluent.intellijplugin.data.ConfluentDataPlaneManager
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import javax.swing.Icon

/**
 * Driver for Confluent Cloud resources.
 * Provides a hierarchical tree structure:
 * - Root
 *   - Environments
 *     - Clusters folder
 *       - Individual Cluster
 *         - Topics folder
 *           - Individual Topic
 *     - Schema Registry folder
 *       - Individual Schema Registry
 */
class ConfluentDriver(
    override val connectionData: ConfluentConnectionData,
    project: Project?,
    testConnection: Boolean
) : MonitoringDriver(project, testConnection) {

    private val log = Logger.getInstance(ConfluentDriver::class.java)

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
        log.info("ConfluentDriver.doLoadChildren: path=${rfsPath.stringRepresentation()}, depth=$depth")

        return when (depth) {
            // Root level: show environments
            0 -> {
                dataManager.getEnvironments().map { env ->
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
                    dataManager.getKafkaClusters(envId).map { cluster ->
                        ConfluentFileInfo(this, clusterPath(envId, cluster.id))
                    }
                } else if (rfsPath.name == SCHEMA_REGISTRY_FOLDER) {
                    val envId = rfsPath.parent?.name ?: return emptyList()
                    dataManager.getSchemaRegistry(envId).map { sr ->
                        ConfluentFileInfo(this, schemaRegistryPath(envId, sr.id))
                    }
                } else {
                    emptyList()
                }
            }
            // Cluster/SR level: show sub-resources (Topics, Schemas, etc.)
            3 -> {
                if (rfsPath.parent?.name == CLUSTERS_FOLDER) {
                    val envId = rfsPath.elements[0]
                    val clusterId = rfsPath.name
                    log.info("ConfluentDriver: Loading children for cluster $clusterId - returning Topics folder")
                    listOf(
                        ConfluentFileInfo(this, topicsFolderPath(envId, clusterId))
                    )
                } else if (rfsPath.parent?.name == SCHEMA_REGISTRY_FOLDER) {
                    val envId = rfsPath.elements[0]
                    val srId = rfsPath.name
                    log.info("ConfluentDriver: Loading children for schema registry $srId - returning Schemas folder")
                    listOf(
                        ConfluentFileInfo(this, schemasFolderPath(envId, srId))
                    )
                } else {
                    log.info("ConfluentDriver: Depth 3 but not a cluster or SR, parent=${rfsPath.parent?.name}")
                    emptyList()
                }
            }
            // Topics/Schemas folder: show individual topics/schemas
            4 -> {
                if (rfsPath.name == TOPICS_FOLDER) {
                    val envId = rfsPath.elements[0]
                    val clusterId = rfsPath.elements[2]

                    log.info("ConfluentDriver: Loading topics for cluster $clusterId")

                    // Get cluster object to create data plane manager
                    val cluster = dataManager.getKafkaClusters(envId)
                        .find { it.id == clusterId }

                    if (cluster == null) {
                        log.warn("ConfluentDriver: Cluster $clusterId not found")
                        return emptyList()
                    }

                    val dataPlaneManager = ConfluentDataPlaneManager(project, cluster)
                    val topics = dataPlaneManager.getTopics()
                    log.info("ConfluentDriver: Found ${topics.size} topics for cluster $clusterId")

                    topics.map { topic ->
                        ConfluentFileInfo(this, topicPath(envId, clusterId, topic.name))
                    }
                } else if (rfsPath.name == SCHEMAS_FOLDER) {
                    val envId = rfsPath.elements[0]
                    val srId = rfsPath.elements[2]

                    log.info("ConfluentDriver: Loading schemas for schema registry $srId")

                    // Get SR object to create schema data plane manager
                    val schemaRegistry = dataManager.getSchemaRegistry(envId)
                        .find { it.id == srId }

                    if (schemaRegistry == null) {
                        log.warn("ConfluentDriver: Schema Registry $srId not found")
                        return emptyList()
                    }

                    val schemaManager = io.confluent.intellijplugin.data.SchemaDataPlaneManager(project, schemaRegistry)
                    val subjects = schemaManager.getSubjects()
                    log.info("ConfluentDriver: Found ${subjects.size} schemas for schema registry $srId")

                    subjects.map { subject ->
                        ConfluentFileInfo(this, schemaPath(envId, srId, subject))
                    }
                } else {
                    log.info("ConfluentDriver: Depth 4 but not Topics or Schemas folder, name=${rfsPath.name}")
                    emptyList()
                }
            }
            // Topic level: leaf nodes (for now, can be extended for partitions, configs, etc.)
            else -> emptyList()
        }
    }

    // Path utilities for hierarchical structure
    private fun environmentPath(envId: String) = RfsPath(listOf(envId), true)
    private fun clustersPath(envId: String) = RfsPath(listOf(envId, CLUSTERS_FOLDER), true)
    private fun schemaRegistryFolderPath(envId: String) = RfsPath(listOf(envId, SCHEMA_REGISTRY_FOLDER), true)
    private fun clusterPath(envId: String, clusterId: String) = RfsPath(listOf(envId, CLUSTERS_FOLDER, clusterId), true)  // Changed to true - clusters now have children (Topics folder)
    private fun schemaRegistryPath(envId: String, srId: String) = RfsPath(listOf(envId, SCHEMA_REGISTRY_FOLDER, srId), true)  // Changed to true - SR now has children (Schemas folder)
    private fun topicsFolderPath(envId: String, clusterId: String) =
        RfsPath(listOf(envId, CLUSTERS_FOLDER, clusterId, TOPICS_FOLDER), true)
    private fun topicPath(envId: String, clusterId: String, topicName: String) =
        RfsPath(listOf(envId, CLUSTERS_FOLDER, clusterId, TOPICS_FOLDER, topicName), false)
    private fun schemasFolderPath(envId: String, srId: String) =
        RfsPath(listOf(envId, SCHEMA_REGISTRY_FOLDER, srId, SCHEMAS_FOLDER), true)
    private fun schemaPath(envId: String, srId: String, subjectName: String) =
        RfsPath(listOf(envId, SCHEMA_REGISTRY_FOLDER, srId, SCHEMAS_FOLDER, subjectName), false)

    companion object {
        const val CLUSTERS_FOLDER = "Clusters"
        const val SCHEMA_REGISTRY_FOLDER = "Schema Registry"
        const val TOPICS_FOLDER = "Topics"
        const val SCHEMAS_FOLDER = "Schemas"

        // Path type checks using size instead of depth
        val RfsPath.isEnvironment: Boolean get() = elements.size == 1
        val RfsPath.isClustersFolder: Boolean get() = elements.size == 2 && name == CLUSTERS_FOLDER
        val RfsPath.isSchemaRegistryFolder: Boolean get() = elements.size == 2 && name == SCHEMA_REGISTRY_FOLDER
        val RfsPath.isCluster: Boolean get() = elements.size == 3 && parent?.name == CLUSTERS_FOLDER
        val RfsPath.isSchemaRegistry: Boolean get() = elements.size == 3 && parent?.name == SCHEMA_REGISTRY_FOLDER
        val RfsPath.isTopicsFolder: Boolean get() = elements.size == 4 && name == TOPICS_FOLDER
        val RfsPath.isTopic: Boolean get() = elements.size == 5 && parent?.name == TOPICS_FOLDER
        val RfsPath.isSchemasFolder: Boolean get() = elements.size == 4 && name == SCHEMAS_FOLDER
        val RfsPath.isSchema: Boolean get() = elements.size == 5 && parent?.name == SCHEMAS_FOLDER

        // Get environment ID from any path
        fun RfsPath.getEnvironmentId(): String? = elements.firstOrNull()

        // Get cluster ID from paths that include cluster
        fun RfsPath.getClusterId(): String? = elements.getOrNull(2)

        // Get schema registry ID from paths that include SR
        fun RfsPath.getSchemaRegistryId(): String? =
            if (elements.size >= 3 && elements.getOrNull(1) == SCHEMA_REGISTRY_FOLDER) elements[2] else null
    }
}

