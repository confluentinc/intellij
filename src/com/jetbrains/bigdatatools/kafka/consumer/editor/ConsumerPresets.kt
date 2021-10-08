package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import com.jetbrains.bigdatatools.ui.MigPanel
import java.awt.Component
import javax.swing.*

class RunConsumerConfigCellRenderer : ListCellRenderer<RunConsumerConfig> {

  private val topicLabel = JLabel()
  private val keyLabel = JLabel()
  private val valueLabel = JLabel()

  private val component = MigPanel().apply {
    row(topicLabel)
    row(keyLabel)
    row(valueLabel)
    row(JSeparator())
  }

  override fun getListCellRendererComponent(list: JList<out RunConsumerConfig>,
                                            value: RunConsumerConfig?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {

    if (isSelected) {
      component.background = list.selectionBackground
      topicLabel.foreground = list.selectionForeground
      keyLabel.foreground = list.selectionForeground
      valueLabel.foreground = list.selectionForeground
    }
    else {
      component.background = list.background
      topicLabel.foreground = list.foreground
      keyLabel.foreground = list.foreground
      valueLabel.foreground = list.foreground
    }


    @Suppress("HardCodedStringLiteral")
    topicLabel.text = if (value?.topic.isNullOrEmpty()) "No topic" else value?.topic

    @Suppress("HardCodedStringLiteral")
    keyLabel.text = "Key: ${value?.keyType?.value ?: "none"}"
    @Suppress("HardCodedStringLiteral")
    valueLabel.text = "Value: ${value?.valueType?.value ?: "none"}"

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