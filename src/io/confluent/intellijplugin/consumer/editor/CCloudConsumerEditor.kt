package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import io.confluent.intellijplugin.common.models.TopicInEditor
import io.confluent.intellijplugin.data.ClusterScopedDataManager
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * File editor for CCloud consumer panel.
 *
 * Uses REST API for consuming records from Confluent Cloud topics.
 * The panel is stored in [ClusterScopedDataManager.consumerPanelStorage] to survive
 * editor recreations (e.g., when the window is moved or floated).
 */
class CCloudConsumerEditor(
    private val project: Project,
    private val clusterDataManager: ClusterScopedDataManager,
    private val file: VirtualFile,
    topic: String?
) : FileEditor, UserDataHolderBase() {

    internal val consumerPanel = clusterDataManager.consumerPanelStorage.getOrCreate(project, file)
    private val mainComponent = consumerPanel.getComponent()

    init {
        topic?.let { consumerPanel.topicComboBox.item = TopicInEditor(it) }
    }

    override fun dispose() {
        clusterDataManager.consumerPanelStorage.unsubscribe(file)
    }

    override fun getName(): String = KafkaMessagesBundle.message("consume.from.topic")
    override fun getComponent(): JComponent = mainComponent
    override fun getPreferredFocusedComponent(): JComponent = mainComponent
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null
}
