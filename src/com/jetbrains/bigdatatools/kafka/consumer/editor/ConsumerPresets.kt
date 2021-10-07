package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import com.jetbrains.bigdatatools.ui.MigPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

class RunConsumerConfigCellRenderer : ListCellRenderer<RunConsumerConfig> {

  private val title = JLabel("Untitled")
  private val topic = JLabel()

  private val component = MigPanel().apply {
    row(title)
    row(topic)
  }

  override fun getListCellRendererComponent(list: JList<out RunConsumerConfig>?,
                                            value: RunConsumerConfig?,
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

class ConsumerPresets {
  private val model = DefaultListModel<RunConsumerConfig>()
  private val presetsPanel = JBList(model).apply {
    cellRenderer = RunConsumerConfigCellRenderer()
  }

  val component = ToolbarDecorator.createDecorator(presetsPanel).setRemoveAction {

  }.createPanel().apply {
    border = JBUI.Borders.empty()
  }

  init {
    model.addAll(KafkaConfigStorage.instance.loadConsumerConfigs())

    //  KafkaConfigStorage.instance.
  }
}