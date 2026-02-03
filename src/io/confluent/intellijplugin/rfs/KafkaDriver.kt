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
import io.confluent.intellijplugin.core.util.invokeLater
import javax.swing.Icon

class KafkaDriver(override val connectionData: KafkaConnectionData, project: Project?, testConnection: Boolean) :
    MonitoringDriver(
        project,
        testConnection
    ) {
    private var hasTrackedConnection = false
    override val dataManager: KafkaDataManager = KafkaDataManager(
        project, connectionData,
        KafkaToolWindowSettings.getInstance(),
        { this }
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
                invokeLater {
                    fileInfoManager.refreshFiles(topicPath)
                }
            }
        })
        dataManager.consumerGroupsModel.addListener(object : DataModelListener {
            override fun onChanged() {
                invokeLater {
                    fileInfoManager.refreshFiles(consumerPath)
                }
            }
        })

        dataManager.schemaRegistryModel?.addListener(object : DataModelListener {
            override fun onChanged() {
                invokeLater {
                    fileInfoManager.refreshFiles(schemasPath)
                }
            }
        })
    }

    /**
     * Wrapper for each connection driver to send telemetry when it's a new created connection or a test connection.
     */
    override fun innerRefreshConnection(calledByUser: Boolean): ReadyConnectionStatus {
        val status = super.innerRefreshConnection(calledByUser)

        if (!hasTrackedConnection || testConnection) {
            val actionType = if (testConnection) "Test" else "Create"
            // prevent sending new connection telemetry when connections are refreshed automatically or manually
            hasTrackedConnection = true

            val errorType = if (status is FailedConnectionStatus) {
                status.getException()::class.simpleName ?: "Unknown"
            } else null

            logUsage(ConnectionEvent(
                action = actionType,
                brokerConfigurationSource = connectionData.brokerConfigurationSource.name,
                // propertySource is only an option when broker config source is Properties
                propertySource = if (connectionData.brokerConfigurationSource == KafkaConfigurationSource.FROM_PROPERTIES) connectionData.propertySource.name else null,
                // brokerCloudSource defaults to Confluent regardless of broker config source, so only track when source is actually Cloud
                cloudType = if (connectionData.brokerConfigurationSource == KafkaConfigurationSource.CLOUD) connectionData.brokerCloudSource.name else null,
                // checks if any Custom or Properties configuration is a Confluent Cloud connection.
                hasCCloudDomain = if (connectionData.brokerConfigurationSource !== KafkaConfigurationSource.CLOUD) connectionData.uri.lowercase().contains("confluent.cloud") else null,
                schemaRegistryType = connectionData.registryType.name,
                withSshTunnel = connectionData.getTunnelData().isEnabled,
                kafkaAuthMethod = determineAuthMethod(connectionData),
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
