package io.confluent.intellijplugin.rfs

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringRfsTreeNode
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.model.ConsumerGroupPresentable
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isConsumers
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isSchemas
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.toolwindow.KafkaMonitoringToolWindowController

class KafkaRfsTreeNode(
    project: Project,
    rfsPath: RfsPath,
    private val topic: TopicPresentable?,
    private val schema: KafkaSchemaInfo?,
    private val consumerGroup: ConsumerGroupPresentable?,
    driver: KafkaDriver,
) : MonitoringRfsTreeNode(project, rfsPath, driver) {
    init {
        myName = rfsPath.name
    }

    override fun isAlwaysLeaf() = rfsPath.isFile

    override fun onDoubleClick(): Boolean {
        val project = project ?: return true
        val controller = driver.getController(project) as? KafkaMonitoringToolWindowController
        controller?.focusOn(focusId, rfsPath)
        return true
    }

    override fun getGrayText(): String? {
        val kafkaFileInfo = fileInfo as? KafkaFileInfo ?: return null
        return when {
            rfsPath.parent?.isSchemas == true -> {
                kafkaFileInfo.driver.dataManager.getCachedSchema(rfsPath.name)?.type?.presentable
            }

            else -> null
        }
    }


    override fun getIdleIcon() = when {
        rfsPath.isRoot -> super.getIdleIcon()
        rfsPath.parent?.isTopicFolder == true && topic?.isFavorite == true -> AllIcons.Nodes.Favorite
        rfsPath.parent?.isSchemas == true && schema?.isFavorite == true -> AllIcons.Nodes.Favorite
        rfsPath.parent?.isConsumers == true && consumerGroup?.isFavorite == true -> AllIcons.Nodes.Favorite
        else -> null
    }
}