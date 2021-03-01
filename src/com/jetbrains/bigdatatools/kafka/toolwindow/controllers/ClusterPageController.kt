package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import java.awt.BorderLayout
import javax.swing.*

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
class ClusterPageController(private val project: Project, private val connectionData: KafkaConnectionData) : Disposable {
  private val topicsController = TopicsController(project, connectionData)

  private val dataManager = KafkaDataManager.getInstance(connectionData.innerId, project) ?: error("Data Manager is not initialized")

  private val model = DefaultListModel<ClusterControllerType>().also { model ->
    ClusterControllerType.values().forEach {
      model.addElement(it)
    }
  }

  private val details = JPanel(BorderLayout())

  private val list = JBList(model).apply {
    selectionMode = DefaultListSelectionModel.SINGLE_SELECTION

    addListSelectionListener { e ->
      if (e.valueIsAdjusting)
        return@addListSelectionListener
      showDetails(selectedValue)
    }
  }

  init {
    Disposer.register(this, topicsController)
  }

  override fun dispose() {}

  fun getComponent() = panel

  private val panel = JPanel(BorderLayout()).apply {
    add(JBScrollPane(list), BorderLayout.LINE_START)
    add(details, BorderLayout.CENTER)
  }


  private fun showDetails(selectedValue: ClusterControllerType) {
    details.removeAll()

    val component = when (selectedValue) {
      ClusterControllerType.TOPIC -> topicsController.getComponent()
      ClusterControllerType.CONSUMER_GROUP -> JLabel("TODO CONSUMER GROUPS")
    }

    details.add(component)

    details.revalidate()
    details.repaint()
  }


  private enum class ClusterControllerType {
    TOPIC, CONSUMER_GROUP
  }
}
