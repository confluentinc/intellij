package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import io.confluent.intellijplugin.common.editor.KafkaEditorProvider
import io.confluent.intellijplugin.common.models.KafkaEditorType
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.settings.actions.CreateConnectionPopup
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.data.CCloudOrgManager
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isTopic
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.toolwindow.controllers.KafkaFileType
import javax.swing.JComponent

class KafkaCreateProducerAction : DumbAwareAction(), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Check for native Kafka data manager first
        val kafkaDataManager = e.dataManager as? KafkaDataManager
        if (kafkaDataManager != null) {
            val rfsPath = e.rfsPath
            val defaultTopic = if (rfsPath?.parent?.isTopicFolder == true)
                rfsPath.name
            else
                null
            openProducer(kafkaDataManager, project, defaultTopic)
            return
        }

        // Check for CCloud data manager
        val ccloudDataManager = e.dataManager as? CCloudClusterDataManager
        if (ccloudDataManager != null) {
            val rfsPath = e.rfsPath
            val defaultTopic = if (rfsPath?.isTopic == true) rfsPath.name else null
            openProducer(ccloudDataManager, project, defaultTopic)
            return
        }

        // Fallback: show popup to select connection
        val actions = DriverManager.getDrivers(project).filterIsInstance<KafkaDriver>().map { driver ->
            create(driver.connectionData.name) {
                openProducer(driver.dataManager, project, null)
            }
        }
        val additional =
            listOf(Separator(), ActionManager.getInstance().getAction("Kafka.GlobalCreateKafkaConnection"))
        CreateConnectionPopup.createPopup(DefaultActionGroup(actions + additional), e)
            .showCenteredInCurrentWindow(project)
    }

    override fun update(e: AnActionEvent) {
        val isKafkaManager = e.dataManager is KafkaDataManager
        val isCCloudClusterManager = e.dataManager is CCloudClusterDataManager
        val isCCloudOrgManager = e.dataManager is CCloudOrgManager

        // Always visible for Kafka or any CCloud context
        e.presentation.isVisible = isKafkaManager || isCCloudClusterManager || isCCloudOrgManager
        // Enabled for native Kafka and CCloud cluster connections
        e.presentation.isEnabled = isKafkaManager || isCCloudClusterManager
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        fun openProducer(dataManager: BaseClusterDataManager, project: Project, defaultTopic: String?): Array<FileEditor> {
            val connectionName = when (dataManager) {
                is KafkaDataManager -> dataManager.connectionData.name
                is CCloudClusterDataManager -> dataManager.connectionData.name
                else -> "Unknown"
            }
            val file = LightVirtualFile("$connectionName Producer", KafkaFileType(), "").apply {
                when (dataManager) {
                    is KafkaDataManager -> putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
                    is CCloudClusterDataManager -> putUserData(KafkaEditorProvider.CCLOUD_MANAGER_KEY, dataManager)
                }
                putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.PRODUCER)
                putUserData(KafkaEditorProvider.KAFKA_DEFAULT_TOPIC, defaultTopic)
            }
            return FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
}