package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.StatusText
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.DetailsMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.BorderLayout
import javax.swing.JComponent

class KafkaTopicSchemaController(private val project: Project,
                                 private val dataManager: KafkaDataManager,
                                 private val viewType: TopicSchemaViewType) : DetailsMonitoringController<String> {
  private var topicName: String? = null

  private val schemaController = KafkaSchemaController(project, dataManager).also {
    Disposer.register(this, it)
  }
  private val curComponent = JBPanelWithEmptyText(BorderLayout())
  private val internalComponent = schemaController.getComponent()

  private val listener = object : DataModelListener {
    override fun onChanged() {
      setDetailsId(topicName ?: "")
    }
  }

  init {
    init()
    setDetailsId(topicName ?: "")
  }

  override fun dispose() {
    dataManager.schemaRegistryModel?.removeListener(listener)
  }

  fun init() {
    setEmptyText()
    dataManager.schemaRegistryModel?.addListener(listener)
  }

  override fun getComponent(): JComponent = curComponent

  override fun setDetailsId(id: String) {
    topicName = id
    val schemaName = id + viewType.suffix
    if (dataManager.isSchemaExists(schemaName))
      setSchemaForTopic(schemaName)
    else
      setEmptySchemaForTopic()

    curComponent.revalidate()
    curComponent.repaint()
  }

  private fun setSchemaForTopic(schemaName: String) {
    curComponent.add(internalComponent, BorderLayout.CENTER)
    schemaController.setDetailsId(schemaName)
  }

  private fun setEmptyText() {
    curComponent.emptyText.clear()
    curComponent.emptyText.isShowAboveCenter = false
  }

  private fun setEmptySchemaForTopic() {
    curComponent.removeAll()
    curComponent.emptyText.clear()
    curComponent.emptyText.appendText(KafkaMessagesBundle.message("topic.schema.empty.text", viewType.title.lowercase()),
                                      StatusText.DEFAULT_ATTRIBUTES)
    curComponent.emptyText.appendSecondaryText(KafkaMessagesBundle.message("topic.schema.empty.text.create.link"),
                                               SimpleTextAttributes.LINK_ATTRIBUTES) {
      val topicName = topicName ?: return@appendSecondaryText
      val dialog = KafkaRegistryAddSchemaDialog(project, dataManager)
      dialog.applySchemaName(topicName, viewType)
      dialog.topicField.isEnabled = false
      dialog.strategyCombobox.isEnabled = false
      dialog.keyValueCombobox.isEnabled = false
      dialog.subjectNameField.isEnabled = false
      dialog.show()
    }
    curComponent.emptyText.isShowAboveCenter = false
  }
}