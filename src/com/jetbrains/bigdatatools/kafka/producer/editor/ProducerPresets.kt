package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.ui.dsl.builder.panel
import com.jetbrains.bigdatatools.kafka.common.editor.Presets
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class RunProducerConfigCellRenderer : ListCellRenderer<StorageProducerConfig> {
  private val topicLabel = JLabel()
  private val keyLabel = JLabel()
  private val valueLabel = JLabel()

  private val component = panel {
    row { cell(topicLabel) }
    row { cell(keyLabel) }
    row { cell(valueLabel) }
    separator()
  }

  override fun getListCellRendererComponent(list: JList<out StorageProducerConfig>,
                                            value: StorageProducerConfig?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    if (isSelected) {
      component.background = list.selectionBackground
    }
    else {
      component.background = list.background
    }

    topicLabel.text = KafkaMessagesBundle.message("producer.preset.topic", if (value?.topic.isNullOrEmpty()) KafkaMessagesBundle.message(
      "producer.preset.no.topic")
    else value?.topic ?: "")
    keyLabel.text = KafkaMessagesBundle.message("producer.preset.key",
                                                value?.takeKeyType()?.title ?: KafkaMessagesBundle.message("producer.preset.none"),
                                                if (value?.key.isNullOrEmpty()) KafkaMessagesBundle.message("producer.preset.no.key")
                                                else value?.key ?: "")
    valueLabel.text = KafkaMessagesBundle.message("producer.preset.value",
                                                  value?.takeValueType()?.title ?: KafkaMessagesBundle.message("producer.preset.none"),
                                                  if (value?.value.isNullOrEmpty()) KafkaMessagesBundle.message(
                                                    "producer.preset.no.value")
                                                  else value?.value ?: "")

    component.border = BorderFactory.createEmptyBorder(0, 10, 0, 5)
    return component
  }
}

class ProducerPresets : Presets<StorageProducerConfig>(KafkaConfigStorage.getInstance().producerConfig, RunProducerConfigCellRenderer())