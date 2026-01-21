package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.projectview.actions.RfsActionPlaces
import io.confluent.intellijplugin.core.util.ToolbarUtils
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.registry.confluent.controller.KafkaRegistryController
import io.confluent.intellijplugin.registry.confluent.controller.KafkaSchemaController
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isConsumers
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isSchemas
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.util.KafkaControllerUtils
import java.awt.BorderLayout

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
internal class KafkaMainController(project: Project, connectionData: KafkaConnectionData) :
    MainTreeController<KafkaConnectionData, KafkaDriver>(project, connectionData) {
    override val dataManager: KafkaDataManager = driver.dataManager

    private val topicsController = TopicsController(project, dataManager, this).also { Disposer.register(this, it) }

    private val consumerGroupsController =
        ConsumerGroupsController(dataManager, this).also { Disposer.register(this, it) }

    private val registryController = if (dataManager.registryType != KafkaRegistryType.NONE)
        KafkaRegistryController(project, dataManager, this).also { Disposer.register(this, it) }
    else
        null

    private val topicInfoController = TopicDetailsController(project, dataManager).also { Disposer.register(this, it) }
    private val consumerOffsetsController =
        ConsumerGroupOffsetsController(dataManager).also { Disposer.register(this, it) }

    private val schemaInfoController = if (dataManager.registryType != KafkaRegistryType.NONE)
        KafkaSchemaController(project, dataManager).also { Disposer.register(this, it) }
    else
        null

    init {
        init()
    }

    override fun createTreePanel(): SimpleToolWindowPanel {
        val scroll = JBScrollPane(myTree).apply {
            border = IdeBorderFactory.createBorder(SideBorder.LEFT)
        }

        return SimpleToolWindowPanel(true, true).apply {
            add(scroll, BorderLayout.CENTER)
        }
    }

    override fun selectDefaultPath() {
        myTree.selectionPath = treeModel.getTreePath(KafkaDriver.topicPath)
    }

    override fun createToolbar(): ActionToolbar? =
        ToolbarUtils.createActionToolbar("KafkaMainController", KafkaControllerUtils.createTopicToolbar(), true)

    override fun setupDriverSpecificTreeInit() {
        PopupHandler.installPopupMenu(myTree, "Kafka.Actions", RfsActionPlaces.RFS_PANE_POPUP)
        details.add(topicsController.getComponent(), KafkaGroupType.TOPIC.name)
        details.add(topicInfoController.getComponent(), KafkaGroupType.TOPIC_DETAIL.name)
        details.add(consumerGroupsController.getComponent(), KafkaGroupType.CONSUMER_GROUP.name)
        details.add(consumerOffsetsController.getComponent(), KafkaGroupType.CONSUMER_GROUP_OFFSET.name)
        registryController?.let {
            details.add(it.getComponent(), KafkaGroupType.SCHEMA_REGISTRY_GROUP.name)
        }
        schemaInfoController?.let {
            details.add(it.getComponent(), KafkaGroupType.SCHEMA_DETAIL.name)
        }
    }

    override fun showDetailsComponent(rfsPath: RfsPath?) {
        when {
            rfsPath == null || rfsPath.isRoot -> {
                showDetailsComponent(KafkaGroupType.TOPIC)
            }

            rfsPath.isTopicFolder -> {
                showDetailsComponent(KafkaGroupType.TOPIC)
            }

            rfsPath.isConsumers -> {
                showDetailsComponent(KafkaGroupType.CONSUMER_GROUP)
            }

            rfsPath.isSchemas -> {
                showDetailsComponent(KafkaGroupType.SCHEMA_REGISTRY_GROUP)
            }

            rfsPath.parent?.isConsumers == true -> {
                showDetailsComponent(KafkaGroupType.CONSUMER_GROUP_OFFSET)
                consumerOffsetsController.setDetailsId(rfsPath.name)
            }

            rfsPath.parent?.isTopicFolder == true -> {
                showDetailsComponent(KafkaGroupType.TOPIC_DETAIL)
                topicInfoController.setDetailsId(rfsPath.name)
            }

            rfsPath.parent?.isSchemas == true -> {
                showDetailsComponent(KafkaGroupType.SCHEMA_DETAIL)
                schemaInfoController?.setDetailsId(rfsPath.name)
            }
        }
    }

    private fun showDetailsComponent(selectedValue: KafkaGroupType?) = detailsLayout.show(details, selectedValue?.name)
}