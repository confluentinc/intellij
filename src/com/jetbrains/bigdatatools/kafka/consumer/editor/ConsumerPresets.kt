package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import java.util.*
import javax.swing.DefaultListModel

fun interface PresetChangeListener : EventListener {
  fun tabChanged(config: RunConsumerConfig)
}

class ConsumerPresets {
  private val model = DefaultListModel<RunConsumerConfig>()
  private val presetsPanel = JBList<Any>(model)

  val component = ToolbarDecorator.createDecorator(JBScrollPane(presetsPanel)).setAddAction {

  }.setRemoveAction {

  }.createPanel()

  init {
    model.addAll(KafkaConfigStorage.instance.loadConsumerConfigs())
  }
}