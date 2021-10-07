package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.producer.models.RunProducerConfig
import com.jetbrains.bigdatatools.ui.MigPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

class RunProducerConfigCellRenderer : ListCellRenderer<RunProducerConfig> {

  private val title = JLabel("Untitled")
  private val topic = JLabel()

  private val component = MigPanel().apply {
    add(title)
    add(topic)
  }

  override fun getListCellRendererComponent(list: JList<out RunProducerConfig>?,
                                            value: RunProducerConfig?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {

    if (isSelected) {
      component.background = list!!.selectionBackground
      topic.foreground = list.selectionForeground
    }
    else {
      component.background = list!!.background
      topic.foreground = list.foreground
    }

    @Suppress("HardCodedStringLiteral")
    topic.text = if (value?.topic.isNullOrEmpty()) "No topic" else value?.topic
    return component
  }
}

class ProducerPresets {
  private val model = DefaultListModel<RunProducerConfig>()
  private val presetsPanel = JBList(model).apply {
    cellRenderer = RunProducerConfigCellRenderer()
  }

  val component = ToolbarDecorator.createDecorator(presetsPanel).setRemoveAction {

  }.createPanel().apply {
    border = JBUI.Borders.empty()
  }

  init {
    model.addAll(KafkaConfigStorage.instance.loadProducerConfigs())
  }
}