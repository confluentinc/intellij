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
import io.confluent.intellijplugin.data.CCloudOrgManager
import io.confluent.intellijplugin.toolwindow.NavigableController
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.util.KafkaMessagesBundle.message
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

    private val logger = Logger.getInstance(ConfluentDriver::class.java)

    var selectedEnvironmentId: String? = null
    private val registeredClusterListeners = mutableSetOf<String>()
    private val registeredSchemaListeners = mutableSetOf<String>()

    // Weak reference to the main controller for navigation
    internal var mainController: NavigableController? = null

    override val dataManager: CCloudOrgManager = CCloudOrgManager(
        project, connectionData, KafkaToolWindowSettings.getInstance(), { this }
    )

    override val presentableName: String = connectionData.name
    override val icon: Icon = AllIcons.Nodes.Folder

    override val treeNodeBuilder: RfsDriverTreeNodeBuilder = object : RfsDriverTreeNodeBuilder() {
        override fun createNode(project: Project, path: RfsPath, driver: Driver): ConfluentRfsTreeNode {
            return ConfluentRfsTreeNode(project, path, this@ConfluentDriver)
        }
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
                invokeLater {
                    fileInfoManager.refreshFiles(clusterPath(clusterId))
                }
            }
        })
    }

    fun registerListenersForEnvironment(envId: String) {
        val clusters = dataManager.client.getCachedKafkaClusters(envId) ?: return
        clusters.forEach { cluster ->
            registerClusterTopicListener(cluster.id, cluster)
        }
    }

    private fun registerSchemaRegistryListener(srId: String, cluster: Cluster) {
        if (registeredSchemaListeners.contains(srId)) return
        registeredSchemaListeners.add(srId)

        val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)
        clusterDataManager.schemaRegistryModel?.addListener(object : DataModelListener {
            override fun onChanged() {
                invokeLater {
                    fileInfoManager.refreshFiles(schemaRegistryPath(srId))
                }
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
        val currentEnvId = selectedEnvironmentId
        logger.info("ConfluentDriver.doLoadChildren: path=${rfsPath.stringRepresentation()}, depth=$depth, selectedEnv=$currentEnvId")

        return when (depth) {
            0 -> {
                val envId = currentEnvId ?: run {
                    logger.warn("ConfluentDriver: No environment selected")
                    return emptyList()
                }

                logger.info("ConfluentDriver: Loading root for environment $envId")

                val clusters = dataManager.client.getCachedKafkaClusters(envId)?.map { cluster ->
                    ConfluentFileInfo(this, clusterPath(cluster.id))
                } ?: emptyList()

                val schemaRegistry = dataManager.client.getCachedSchemaRegistry(envId)?.let { sr ->
                    ConfluentFileInfo(this, schemaRegistryPath(sr.id))
                }

                if (selectedEnvironmentId != currentEnvId) {
                    logger.info("ConfluentDriver: Environment changed during load, returning empty")
                    return emptyList()
                }

                clusters + listOfNotNull(schemaRegistry)
            }
            1 -> {
                val envId = currentEnvId ?: return emptyList()
                val nodeId = rfsPath.name

                val clusters = dataManager.client.getCachedKafkaClusters(envId) ?: return emptyList()

                // Check if it's a cluster
                val cluster = clusters.find { it.id == nodeId }
                if (cluster != null) {
                    logger.info("ConfluentDriver: Loading topics for cluster $nodeId")

                    registerClusterTopicListener(nodeId, cluster)

                    val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)
                    val topics = clusterDataManager.getTopics()
                    logger.info("ConfluentDriver: Found ${topics.size} topics")

                    if (selectedEnvironmentId != currentEnvId) {
                        logger.info("ConfluentDriver: Environment changed during topics load, returning empty")
                        return emptyList()
                    }

                    return when {
                        topics.isEmpty() && clusterDataManager.topicModel.isInitedByFirstTime == false ->
                            listOf(ConfluentFileInfo(this, emptyStatePath(message("confluent.cloud.tree.loading"))))
                        topics.isEmpty() ->
                            listOf(ConfluentFileInfo(this, emptyStatePath(message("confluent.cloud.tree.no.topics"))))
                        else ->
                            topics.map { topic ->
                                ConfluentFileInfo(this, topicPath(nodeId, topic.name))
                            }
                    }
                }

                // Check if it's a schema registry
                val sr = dataManager.client.getCachedSchemaRegistry(envId)
                if (sr != null && sr.id == nodeId) {
                    logger.info("ConfluentDriver: Loading schemas for schema registry $nodeId")

                    val firstCluster = clusters.firstOrNull()

                    if (firstCluster == null) {
                        logger.warn("ConfluentDriver: No clusters found in environment $envId")
                        return emptyList()
                    }

                    registerSchemaRegistryListener(nodeId, firstCluster)

                    val cache = dataManager.getDataPlaneCache(firstCluster)

                    if (!cache.hasSchemaRegistry()) {
                        logger.warn("ConfluentDriver: No Schema Registry available")
                        return emptyList()
                    }

                    val clusterDataManager = dataManager.getOrCreateClusterDataManager(firstCluster)
                    clusterDataManager.initRefreshSchemasIfRequired()

                    val schemas = clusterDataManager.getSchemas()
                    logger.info("ConfluentDriver: Found ${schemas.size} schemas")

                    if (selectedEnvironmentId != currentEnvId) {
                        logger.info("ConfluentDriver: Environment changed during schemas load, returning empty")
                        return emptyList()
                    }

                    return when {
                        schemas.isEmpty() && clusterDataManager.schemaRegistryModel?.isInitedByFirstTime == false ->
                            listOf(ConfluentFileInfo(this, emptyStatePath(message("confluent.cloud.tree.loading"))))
                        schemas.isEmpty() ->
                            listOf(ConfluentFileInfo(this, emptyStatePath(message("confluent.cloud.tree.no.schemas"))))
                        else ->
                            schemas.map { schema ->
                                ConfluentFileInfo(this, schemaPath(nodeId, schema.name))
                            }
                    }
                }

                logger.warn("ConfluentDriver: Node $nodeId not found as cluster or schema registry")
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
            return driver.dataManager.client.getCachedKafkaClusters(envId)?.any { it.id == nodeId } == true ||
                   driver.dataManager.client.getCachedSchemaRegistry(envId)?.id == nodeId
        }

        fun RfsPath.isCluster(driver: ConfluentDriver): Boolean {
            return elements.size == 1 && name.startsWith("lkc-")
        }

        fun RfsPath.isSchemaRegistry(driver: ConfluentDriver): Boolean {
            return elements.size == 1 && name.startsWith("lsrc-")
        }

        val RfsPath.isTopic: Boolean get() = elements.size == 2 && elements[0].startsWith("lkc-")
        val RfsPath.isSchema: Boolean get() = elements.size == 2 && elements[0].startsWith("lsrc-")

        // Path properties matching KafkaDriver for action compatibility
        val RfsPath.isTopicFolder: Boolean get() = elements.size == 1 && elements[0].startsWith("lkc-") && isDirectory
        val RfsPath.isSchemas: Boolean get() = elements.size == 1 && elements[0].startsWith("lsrc-") && isDirectory
        val RfsPath.isConsumers: Boolean get() = false // TODO: Implement consumer groups path validation

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
