package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.ui.MigPanel
import com.jetbrains.bigdatatools.util.BdIdeRegistryUtil
import net.miginfocom.layout.LC
import java.awt.BorderLayout
import javax.swing.*

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
class ClusterPageController(private val project: Project, connectionData: KafkaConnectionData) : Disposable {
  private val dataManager = KafkaDataManager.getInstance(connectionData.innerId, project) ?: error("Data Manager is not initialized")

  private val topicsController = TopicsController(project, dataManager)
  private val consumerGroupsController = ConsumerGroupsController(dataManager)

  private val details = JPanel(BorderLayout())
  private val panel = createPanel()

  init {
    Disposer.register(this, topicsController)
    Disposer.register(this, consumerGroupsController)
  }

  override fun dispose() {}

  fun getComponent() = panel

  private fun showDetails(selectedValue: ClusterControllerType) {
    details.removeAll()

    val component = when (selectedValue) {
      ClusterControllerType.TOPIC -> topicsController.getComponent()
      ClusterControllerType.CONSUMER_GROUP -> consumerGroupsController.getComponent()
    }

    details.add(component)

    details.revalidate()
    details.repaint()
  }

  private fun createPanel(): JPanel {
    val model = DefaultListModel<ClusterControllerType>().also { model ->
      ClusterControllerType.values().forEach {
        model.addElement(it)
      }
    }

    val list = JBList(model).apply {
      cellRenderer = CustomListCellRenderer<ClusterControllerType> { value -> value.value }
      selectionMode = DefaultListSelectionModel.SINGLE_SELECTION
      selectedIndex = 0

      addListSelectionListener { e ->
        if (e.valueIsAdjusting)
          return@addListSelectionListener
        showDetails(selectedValue)
      }
    }

    list.selectedIndex = 0
    showDetails(ClusterControllerType.TOPIC)

    val createProducer = JButton(KafkaMessagesBundle.message("create.producer.action.title")).apply {
      addActionListener {
        val file = LightVirtualFile("Kafka Producer")
        file.putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
        file.putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.PRODUCER)
        FileEditorManagerEx.getInstance(project).openFile(file, true)
      }
    }

    val createConsumer = JButton(KafkaMessagesBundle.message("create.consumer.action.title")).apply {
      addActionListener {
        val file = LightVirtualFile("Kafka Consumer")
        file.putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
        file.putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.CONSUMER)
        FileEditorManagerEx.getInstance(project).openFile(file, true)
      }
    }

    val scroll = JBScrollPane(list).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val leftPanel = if (BdIdeRegistryUtil.isInternalFeaturesAvailable()) {
      JPanel(BorderLayout()).apply {
        add(scroll, BorderLayout.CENTER)
        add(MigPanel(LC().insets("0").gridGapY("0").fillX().hideMode(3)).apply {
          row(createProducer)
          row(createConsumer)
        }, BorderLayout.SOUTH)
      }
    }
    else {
      scroll
    }

    return OnePixelSplitter().apply {
      proportion = 0.01f
      dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
      firstComponent = leftPanel
      secondComponent = details
    }
  }

  private enum class ClusterControllerType(val value: String) {
    TOPIC("Topics"), CONSUMER_GROUP("Consumers")
  }
}
