package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import io.confluent.intellijplugin.common.models.TopicInEditor
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class KafkaConsumerEditor(val project: Project,
                          private val kafkaManager: KafkaDataManager,
                          private val file: VirtualFile,
                          topic: String?) : FileEditor, UserDataHolderBase() {
  internal val customizable = kafkaManager.consumerPanelStorage.getOrCreate(project, file)
  private val mainComponent = customizable.getComponent()

  init {
     topic?.let { customizable.topicComboBox.item = TopicInEditor(it) }
  }

  override fun dispose() {
    kafkaManager.consumerPanelStorage.unsubscribe(file)
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