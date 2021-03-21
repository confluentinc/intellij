package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.tabs.TabInfo
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.ui.JBRunnerTabsBorderless

class TopicDetailsController(project: Project, dataManager: KafkaDataManager) : Disposable {
  private val tabs: JBRunnerTabs = JBRunnerTabsBorderless(project,
                                                          ActionManager.getInstance(),
                                                          IdeFocusManager.getInstance(project),
                                                          this)

  private val configsController = TopicConfigsController(dataManager).also {
    Disposer.register(this, it)
  }

  private val partitionsController = TopicPartitionsController(dataManager).also {
    Disposer.register(this, it)
  }

  init {
    val partitionsTab: TabInfo = TabInfo(partitionsController.getComponent()).apply {
      text = KafkaMessagesBundle.message("topic.tab.partitions")
    }
    tabs.addTab(partitionsTab)

    val configTab: TabInfo = TabInfo(configsController.getComponent()).apply { text = KafkaMessagesBundle.message("topic.tab.configs") }
    tabs.addTab(configTab)
  }

  fun setTopicId(topicId: String) {
    configsController.setDetailsId(topicId)
    partitionsController.setDetailsId(topicId)
  }

  fun getComponent() = tabs.component

  override fun dispose() {}
}