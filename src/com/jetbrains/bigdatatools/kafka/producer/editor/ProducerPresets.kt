package com.jetbrains.bigdatatools.kafka.producer.editor

import com.jetbrains.bigdatatools.kafka.common.editor.Presets
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.ui.MigPanel
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSeparator
import javax.swing.ListCellRenderer

class RunProducerConfigCellRenderer : ListCellRenderer<StorageProducerConfig> {
  private val topicLabel = JLabel()
  private val keyLabel = JLabel()
  private val valueLabel = JLabel()

  private val component = MigPanel().apply {
    row(topicLabel)
    row(keyLabel)
    row(valueLabel)
    row(JSeparator())
  }

  override fun getListCellRendererComponent(list: JList<out StorageProducerConfig>,
                                            value: StorageProducerConfig?,
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

    topicLabel.text = KafkaMessagesBundle.message("producer.preset.topic", if (value?.topic.isNullOrEmpty()) KafkaMessagesBundle.message(
      "producer.preset.no.topic")
    else value?.topic ?: "")
    keyLabel.text = KafkaMessagesBundle.message("producer.preset.key",
                                                value?.getKeyType()?.title ?: KafkaMessagesBundle.message("producer.preset.none"),
                                                if (value?.key.isNullOrEmpty()) KafkaMessagesBundle.message("producer.preset.no.key")
                                                else value?.key ?: "")
    valueLabel.text = KafkaMessagesBundle.message("producer.preset.value",
                                                  value?.getValueType()?.title ?: KafkaMessagesBundle.message("producer.preset.none"),
                                                  if (value?.value.isNullOrEmpty()) KafkaMessagesBundle.message(
                                                    "producer.preset.no.value")
                                                  else value?.value ?: "")
    return component
  }
}

class ProducerPresets : Presets<StorageProducerConfig>(KafkaConfigStorage.instance.producerConfig, RunProducerConfigCellRenderer())