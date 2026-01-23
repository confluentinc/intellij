package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.monitoring.toolwindow.DetailsMonitoringController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
import io.confluent.intellijplugin.core.monitoring.toolwindow.TabbedDetailsMonitoringController
import io.confluent.intellijplugin.data.TopicDetailDataProvider
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.registry.confluent.controller.KafkaTopicSchemaController
import io.confluent.intellijplugin.registry.confluent.controller.TopicSchemaViewType
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class TopicDetailsController(
    project: Project,
    private val dataManager: TopicDetailDataProvider
) : TabbedDetailsMonitoringController<String>(project) {
    private val configsController = TopicConfigsController(project, dataManager).also { Disposer.register(this, it) }
    private val partitionsController = TopicPartitionsController(dataManager).also { Disposer.register(this, it) }

    override val tabsControllers: List<Pair<String, DetailsMonitoringController<String>>> = let {
        val origin = listOf(
            KafkaMessagesBundle.message("topic.tab.partitions") to partitionsController,
            KafkaMessagesBundle.message("topic.tab.configs") to configsController
        )

        val schemas: List<Pair<String, DetailsMonitoringController<String>>> = when (dataManager.registryType) {
            // TODO: Add schema tabs for Confluent Cloud REST API
            KafkaRegistryType.NONE -> emptyList()
            KafkaRegistryType.CONFLUENT -> {
                // Schema tabs for KafkaDataManager 
                if (dataManager is io.confluent.intellijplugin.data.KafkaDataManager) {
                    listOf(
                        KafkaMessagesBundle.message("topic.tab.schema.key") to KafkaTopicSchemaController(
                            project,
                            dataManager,
                            TopicSchemaViewType.KEY
                        ),
                        KafkaMessagesBundle.message("topic.tab.schema.value") to KafkaTopicSchemaController(
                            project,
                            dataManager,
                            TopicSchemaViewType.VALUE
                        )
                    )
                } else {
                    emptyList()
                }
            }
            KafkaRegistryType.AWS_GLUE -> {
                // Schema tabs require KafkaDataManager specifically
                if (dataManager is io.confluent.intellijplugin.data.KafkaDataManager) {
                    listOf(
                        KafkaMessagesBundle.message("topic.tab.schema") to KafkaTopicSchemaController(
                            project,
                            dataManager,
                            TopicSchemaViewType.TOPIC
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }
        schemas.forEach {
            Disposer.register(this, it.second)
        }
        origin + schemas
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        // TopicDetailDataProvider implementations extend MonitoringDataManager
        if (dataManager is io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager) {
            sink[MainTreeController.DATA_MANAGER] = dataManager
        }
        sink[MainTreeController.RFS_PATH] = detailsId?.let { KafkaDriver.topicPath.child(it, false) }
    }

    init {
        init()
    }
}