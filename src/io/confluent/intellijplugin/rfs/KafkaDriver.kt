package io.confluent.intellijplugin.rfs

import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.rfs.driver.*
import io.confluent.intellijplugin.core.rfs.tree.DriverRfsTreeModel
import io.confluent.intellijplugin.core.rfs.tree.node.RfsDriverTreeNodeBuilder
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.telemetry.*
import io.confluent.intellijplugin.toolwindow.KafkaMonitoringToolWindowController
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.toolwindow.controllers.KafkaGroupType
import javax.swing.Icon

class KafkaDriver(override val connectionData: KafkaConnectionData, project: Project?, testConnection: Boolean) :
    MonitoringDriver(
        project,
        testConnection
    ) {
    private var hasTrackedConnection = false
    override val dataManager: KafkaDataManager = KafkaDataManager(
        project, connectionData,
        KafkaToolWindowSettings.getInstance()
    )
    override val presentableName: String = connectionData.name
    override val icon: Icon = BigdatatoolsKafkaIcons.Kafka

    override val treeNodeBuilder: RfsDriverTreeNodeBuilder = object : RfsDriverTreeNodeBuilder() {
        override fun createNode(project: Project, path: RfsPath, driver: Driver) =
            KafkaRfsTreeNode(
                project, path, dataManager.getCachedTopicByName(path.name), dataManager.getSchemaByName(path.name),
                dataManager.getCachedConsumerGroup(path.name), this@KafkaDriver
            )
    }

    init {
        Disposer.register(this, dataManager)

        dataManager.topicModel.addListener(object : DataModelListener {
            override fun onChanged() {
                fileInfoManager.refreshFiles(topicPath)
            }
        })
        dataManager.consumerGroupsModel.addListener(object : DataModelListener {
            override fun onChanged() {
                fileInfoManager.refreshFiles(consumerPath)
            }
        })

        dataManager.schemaRegistryModel?.addListener(object : DataModelListener {
            override fun onChanged() {
                fileInfoManager.refreshFiles(schemasPath)
            }
        })
    }

    /**
     * Wrapper for each connection driver to send telemetry only when it's a new created connection.
     */
    override fun innerRefreshConnection(calledByUser: Boolean): ReadyConnectionStatus {
        val status = super.innerRefreshConnection(calledByUser)

        if (!hasTrackedConnection && !testConnection) {
            hasTrackedConnection = true

            val errorType = if (status is FailedConnectionStatus) {
                status.getException()::class.simpleName ?: "Unknown"
            } else null

            logUsage(ConnectionCreatedEvent(
                connectionType = "Kafka",
                cloudType = connectionData.brokerCloudSource.name,
                hasSchemaRegistry = connectionData.registryType.name != "NONE",
                registryType = connectionData.registryType.name,
                hasSshTunnel = connectionData.getTunnelData().isEnabled,
                authMethod = determineAuthMethod(connectionData),
                success = status == ConnectedConnectionStatus,
                errorType = errorType
            ))
        }

        return status
    }

    override fun dispose() {}

    override fun createTreeModel(rootPath: RfsPath, project: Project) =
        DriverRfsTreeModel(project, rootPath, this, false)

    override fun doLoadFileInfo(rfsPath: RfsPath) = KafkaFileInfo(this, rfsPath)

    override fun doLoadChildren(rfsPath: RfsPath): List<FileInfo>? {
        dataManager.client.connectionError?.let {
            throw it
        }
        val children = when {
            rfsPath.isRoot -> listOfNotNull(
                topicPath,
                if (dataManager.registryType != KafkaRegistryType.NONE) schemasPath else null,
                consumerPath
            )

            rfsPath.isTopicFolder -> {
                dataManager.topicModel.error?.let { throw it }
                dataManager.topicModel.data?.map { topicPath.child(it.name, false) } ?: emptyList()
            }

            rfsPath.isConsumers -> {
                dataManager.consumerGroupsModel.error?.let { throw it }
                dataManager.consumerGroupsModel.data?.map { consumerPath.child(it.consumerGroup, false) } ?: emptyList()
            }

            rfsPath.isSchemas -> {
                dataManager.schemaRegistryModel?.error?.let { throw it }
                dataManager.schemaRegistryModel?.data?.map { schemasPath.child(it.name, false) } ?: emptyList()
            }

            else -> null
        }
        return children?.map { KafkaFileInfo(this, it) }
    }

    override fun getController(project: Project) = KafkaMonitoringToolWindowController.getInstance(project)

    companion object {
        val topicPath = RfsPath(listOf(KafkaGroupType.TOPIC.title), true)
        val consumerPath = RfsPath(listOf(KafkaGroupType.CONSUMER_GROUP.title), true)
        val schemasPath = RfsPath(listOf(KafkaGroupType.SCHEMA_REGISTRY_GROUP.title), true)

        val RfsPath.isTopicFolder
            get() = this == topicPath
        val RfsPath.isConsumers
            get() = this == consumerPath
        val RfsPath.isSchemas
            get() = this == schemasPath
    }
}