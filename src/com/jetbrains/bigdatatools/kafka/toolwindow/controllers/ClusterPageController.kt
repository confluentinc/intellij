package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
class ClusterPageController(project: Project, connectionData: KafkaConnectionData) : Disposable {
  private val dataManager = KafkaDataManager.getInstance(connectionData.innerId, project) ?: error("Data Manager is not initialized")

  private val topicsController = TopicsController(dataManager)
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
      selectionMode = DefaultListSelectionModel.SINGLE_SELECTION

      addListSelectionListener { e ->
        if (e.valueIsAdjusting)
          return@addListSelectionListener
        showDetails(selectedValue)
      }
    }

    list.cellRenderer = ClusterControllerTypeListRenderer()

    return JPanel(BorderLayout()).apply {
      add(JBScrollPane(list), BorderLayout.LINE_START)
      add(details, BorderLayout.CENTER)
    }
  }


  private enum class ClusterControllerType(val value: String) {
    TOPIC("Topics"), CONSUMER_GROUP("Consumers")
  }

  private class ClusterControllerTypeListRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(list: JList<*>?,
                                              value: Any?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component =
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
        (value as? ClusterControllerType)?.let { text = it.value }
      }
  }
}
