package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

class RunConsumerConfigCellRenderer : ListCellRenderer<RunConsumerConfig> {

  private val title = JLabel("Untitled")
  private val topic = JLabel()

  private val component = JPanel(BorderLayout()).apply {
    add(title)
    add(topic)
  }

  override fun getListCellRendererComponent(list: JList<out RunConsumerConfig>?,
                                            value: RunConsumerConfig?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    @Suppress("HardCodedStringLiteral")
    topic.text = value?.topic
    return component
  }
}

class ConsumerPresets {
  private val model = DefaultListModel<RunConsumerConfig>()
  private val presetsPanel = JBList(model).apply {
    cellRenderer = RunConsumerConfigCellRenderer()
  }

  val component = ToolbarDecorator.createDecorator(presetsPanel).setAddAction {

  }.setRemoveAction {

  }.createPanel().apply {
    border = JBUI.Borders.empty()
  }

  init {
    model.addAll(KafkaConfigStorage.instance.loadConsumerConfigs())
  }
}