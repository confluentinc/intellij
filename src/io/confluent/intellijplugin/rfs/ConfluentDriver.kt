package io.confluent.intellijplugin.rfs

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
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
 * Driver for Confluent Cloud resources
 * Tree structure when environment is selected:
 * - Root (filtered to selected environment)
 *   - Individual Clusters
 *     - Individual Topics (direct children)
 *   - Individual Schema Registries
 *     - Individual Schemas (direct children)
 */
class ConfluentDriver(
    override val connectionData: ConfluentConnectionData,
    project: Project?,
    testConnection: Boolean
) : MonitoringDriver(project, testConnection) {

    private val log = Logger.getInstance(ConfluentDriver::class.java)

    var selectedEnvironmentId: String? = null
    private val registeredClusterListeners = mutableSetOf<String>()

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

    private fun registerClusterTopicListener(clusterId: String, cluster: Cluster) {
        if (registeredClusterListeners.contains(clusterId)) return
        registeredClusterListeners.add(clusterId)

        val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)
        clusterDataManager.topicModel.addListener(object : DataModelListener {
            override fun onChanged() {
                fileInfoManager.refreshFiles(clusterPath(clusterId))
            }
        })
    }

    override fun getController(project: Project): MonitoringToolWindowController? = null

    override fun dispose() {}

    override fun createTreeModel(rootPath: RfsPath, project: Project) =
        DriverRfsTreeModel(project, rootPath, this, false)

    override fun doLoadFileInfo(rfsPath: RfsPath): FileInfo = ConfluentFileInfo(this, rfsPath)

    override fun doLoadChildren(rfsPath: RfsPath): List<FileInfo>? {
        dataManager.client.connectionError?.let { throw it }

        val depth = rfsPath.elements.size
        log.info("ConfluentDriver.doLoadChildren: path=${rfsPath.stringRepresentation()}, depth=$depth, selectedEnv=$selectedEnvironmentId")

        return when (depth) {
            0 -> {
                val envId = selectedEnvironmentId ?: run {
                    log.warn("ConfluentDriver: No environment selected")
                    return emptyList()
                }

                log.info("ConfluentDriver: Loading root for environment $envId")

                val clusters = dataManager.client.getKafkaClusters(envId).map { cluster ->
                    ConfluentFileInfo(this, clusterPath(cluster.id))
                }

                val schemaRegistry = dataManager.client.getSchemaRegistry(envId)?.let { sr ->
                    ConfluentFileInfo(this, schemaRegistryPath(sr.id))
                }

                clusters + listOfNotNull(schemaRegistry)
            }
            // Cluster/SR level: show topics/schemas directly
            1 -> {
                val envId = selectedEnvironmentId ?: return emptyList()
                val nodeId = rfsPath.name

                // Check if it's a cluster
                val cluster = dataManager.getKafkaClusters(envId).find { it.id == nodeId }
                if (cluster != null) {
                    log.info("ConfluentDriver: Loading topics for cluster $nodeId")

                    registerClusterTopicListener(nodeId, cluster)

                    val cache = dataManager.getDataPlaneCache(cluster)
                    val topics = cache.refreshTopics().sortedBy { it.topicName.lowercase() }
                    log.info("ConfluentDriver: Found ${topics.size} topics")

                    return if (topics.isEmpty()) {
                        listOf(ConfluentFileInfo(this, emptyStatePath("No topics available")))
                    } else {
                        topics.map { topic ->
                            ConfluentFileInfo(this, topicPath(nodeId, topic.topicName))
                        }
                    }
                }

                // Check if it's a schema registry
                val sr = dataManager.client.getSchemaRegistry(envId)
                if (sr != null && sr.id == nodeId) {
                    log.info("ConfluentDriver: Loading schemas for schema registry $nodeId")

                    val clusters = dataManager.getKafkaClusters(envId)
                    val firstCluster = clusters.firstOrNull()

                    if (firstCluster == null) {
                        log.warn("ConfluentDriver: No clusters found in environment $envId")
                        return emptyList()
                    }

                    val cache = dataManager.getDataPlaneCache(firstCluster)

                    if (!cache.hasSchemaRegistry()) {
                        log.warn("ConfluentDriver: No Schema Registry available")
                        return emptyList()
                    }

                    val subjects = cache.refreshSubjects().sortedBy { it.name.lowercase() }
                    log.info("ConfluentDriver: Found ${subjects.size} schemas")

                    return if (subjects.isEmpty()) {
                        listOf(ConfluentFileInfo(this, emptyStatePath("No schemas available")))
                    } else {
                        subjects.map { subject ->
                            ConfluentFileInfo(this, schemaPath(nodeId, subject.name))
                        }
                    }
                }

                log.warn("ConfluentDriver: Node $nodeId not found as cluster or schema registry")
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun clusterPath(clusterId: String) = RfsPath(listOf(clusterId), true)
    private fun schemaRegistryPath(srId: String) = RfsPath(listOf(srId), true)
    private fun topicPath(clusterId: String, topicName: String) = RfsPath(listOf(clusterId, topicName), false)
    private fun schemaPath(srId: String, subjectName: String) = RfsPath(listOf(srId, subjectName), false)
    private fun emptyStatePath(message: String) = RfsPath(listOf(message), false)

    companion object {
        fun RfsPath.isClusterOrSchemaRegistry(driver: ConfluentDriver): Boolean {
            if (elements.size != 1) return false
            val envId = driver.selectedEnvironmentId ?: return false
            val nodeId = name
            return driver.dataManager.client.getKafkaClusters(envId).any { it.id == nodeId } ||
                   driver.dataManager.client.getSchemaRegistry(envId)?.id == nodeId
        }

        fun RfsPath.isCluster(driver: ConfluentDriver): Boolean {
            if (elements.size != 1) return false
            val envId = driver.selectedEnvironmentId ?: return false
            return driver.dataManager.client.getKafkaClusters(envId).any { it.id == name }
        }

        fun RfsPath.isSchemaRegistry(driver: ConfluentDriver): Boolean {
            if (elements.size != 1) return false
            val envId = driver.selectedEnvironmentId ?: return false
            return driver.dataManager.client.getSchemaRegistry(envId)?.id == name
        }

        val RfsPath.isTopic: Boolean get() = elements.size == 2
        val RfsPath.isSchema: Boolean get() = elements.size == 2

        val RfsPath.isEnvironment: Boolean get() = false
        val RfsPath.isClustersFolder: Boolean get() = false
        val RfsPath.isSchemaRegistryFolder: Boolean get() = false

        fun RfsPath.getEnvironmentId(driver: ConfluentDriver): String? = driver.selectedEnvironmentId

        fun RfsPath.getClusterId(): String? = when (elements.size) {
            1 -> elements[0]
            2 -> elements[0]
            else -> null
        }

        fun RfsPath.getSchemaRegistryId(): String? = when (elements.size) {
            1 -> elements[0]
            2 -> elements[0]
            else -> null
        }
    }
}

