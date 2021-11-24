package com.jetbrains.bigdatatools.kafka.producer.editor

import com.jetbrains.bigdatatools.kafka.common.editor.Presets
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.producer.models.RunProducerConfig
import com.jetbrains.bigdatatools.ui.MigPanel
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSeparator
import javax.swing.ListCellRenderer

class RunProducerConfigCellRenderer : ListCellRenderer<RunProducerConfig> {
  private val topicLabel = JLabel()
  private val keyLabel = JLabel()
  private val valueLabel = JLabel()

  private val component = MigPanel().apply {
    row(topicLabel)
    row(keyLabel)
    row(valueLabel)
    row(JSeparator())
  }

  override fun getListCellRendererComponent(list: JList<out RunProducerConfig>,
                                            value: RunProducerConfig?,
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

    topicLabel.text = "Topic: ${if (value?.topic.isNullOrEmpty()) "No topic" else value?.topic}"
    keyLabel.text = "Key [${value?.keyType?.title ?: "none"}]:  ${if (value?.key.isNullOrEmpty()) "No key" else value?.key}"
    valueLabel.text = "Value [${value?.valueType?.title ?: "none"}]:  ${if (value?.value.isNullOrEmpty()) "No key" else value?.value}"

    return component
  }
}

class ProducerPresets : Presets<RunProducerConfig>(KafkaConfigStorage.instance.producerConfig, RunProducerConfigCellRenderer())