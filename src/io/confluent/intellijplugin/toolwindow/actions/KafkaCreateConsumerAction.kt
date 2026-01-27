package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
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
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.toolwindow.controllers.KafkaFileType
import javax.swing.JComponent

class KafkaCreateConsumerAction : DumbAwareAction(), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dataManager = e.dataManager as? KafkaDataManager
        if (dataManager != null) {
            val rfsPath = e.rfsPath
            val defaultTopic = if (rfsPath?.parent?.isTopicFolder == true)
                rfsPath.name
            else
                null
            createConsumer(project, dataManager, defaultTopic)
        } else {
            val actions = DriverManager.getDrivers(project).filterIsInstance<KafkaDriver>().map { driver ->
                create(driver.connectionData.name) {
                    createConsumer(project, driver.dataManager, null)
                }
            }
            val additional =
                listOf(Separator(), ActionManager.getInstance().getAction("Kafka.GlobalCreateKafkaConnection"))
            CreateConnectionPopup.createPopup(DefaultActionGroup(actions + additional), e)
                .showCenteredInCurrentWindow(project)
        }
    }

    // TODO: Remove this override once consumer support is implemented for CCloud
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.dataManager is KafkaDataManager
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        fun createConsumer(project: Project, dataManager: KafkaDataManager, defaultTopic: String?) {
            val connectionData = dataManager.connectionData
            val file = LightVirtualFile("${connectionData.name} Consumer", KafkaFileType(), "").apply {
                putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
                putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.CONSUMER)
                putUserData(KafkaEditorProvider.KAFKA_DEFAULT_TOPIC, defaultTopic)
            }
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }
}