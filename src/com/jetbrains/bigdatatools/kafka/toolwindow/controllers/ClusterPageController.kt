package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
class ClusterPageController(private val project: Project, private val connectionId: String) : Disposable {
  private val dataManager = KafkaDataManager.getInstance(connectionId, project) ?: error("Data Manager is not initialized")

  private val panel = JPanel(BorderLayout())

  init {
    panel.add(JLabel("Stub!"))
  }

  override fun dispose() {}

  fun getComponent() = panel
}