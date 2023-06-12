package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.common.ui.MigPanel
import com.jetbrains.bigdatatools.kafka.common.editor.Presets
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerFilterType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerLimitType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.Component
import java.text.SimpleDateFormat
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSeparator
import javax.swing.ListCellRenderer

class RunConsumerConfigCellRenderer : ListCellRenderer<StorageConsumerConfig> {

  companion object {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  }

  private val topicLabel = JLabel()
  private val keyLabel = JLabel()
  private val valueLabel = JLabel()
  private val startFromLabel = JLabel()
  private val limitLabel = JLabel()
  private val filterLabel = JLabel()
  private val filterKeyLabel = JLabel()
  private val filterValueLabel = JLabel()
  private val filterHeaderKeyLabel = JLabel()
  private val filterHeaderValueLabel = JLabel()

  private val labels = listOf(topicLabel, keyLabel, valueLabel, startFromLabel, limitLabel, filterLabel, filterKeyLabel, filterValueLabel,
                              filterHeaderKeyLabel, filterHeaderValueLabel)

  private val component = MigPanel().apply {
    labels.forEach { row(it) }
    row(JSeparator())
  }

  override fun getListCellRendererComponent(list: JList<out StorageConsumerConfig>,
                                            value: StorageConsumerConfig,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {

    if (isSelected) {
      component.background = list.selectionBackground
      labels.forEach { it.foreground = list.selectionForeground }
    }
    else {
      component.background = list.background
      labels.forEach { it.foreground = list.foreground }
    }

    topicLabel.text = KafkaMessagesBundle.message("consumer.preset.topic", if (value.topic.isNullOrEmpty()) KafkaMessagesBundle.message(
      "consumer.preset.no.topic")
    else value.topic ?: "")

    keyLabel.text = KafkaMessagesBundle.message("consumer.preset.key", value.getKeyType().title)
    valueLabel.text = KafkaMessagesBundle.message("consumer.preset.value", value.getValueType().title)

    startFromLabel.isVisible = false

    val limit = value.getLimit()
    if (limit.type == ConsumerLimitType.NONE) {
      limitLabel.isVisible = false
    }
    else {
      limitLabel.isVisible = true
      limitLabel.text = KafkaMessagesBundle.message("consumer.preset.limit",
                                                    limit.type.title,
                                                    if (limit.type == ConsumerLimitType.DATE) dateFormat.format(limit.time)
                                                    else limit.value)
    }

    val filter = value.getFilter()

    val filterVisible = filter.type != ConsumerFilterType.NONE

    filterLabel.isVisible = filterVisible
    filterKeyLabel.isVisible = filterVisible && !filter.filterKey.isNullOrEmpty()
    filterValueLabel.isVisible = filterVisible && !filter.filterValue.isNullOrEmpty()
    filterHeaderKeyLabel.isVisible = filterVisible && !filter.filterHeadKey.isNullOrEmpty()
    filterHeaderValueLabel.isVisible = filterVisible && !filter.filterHeadValue.isNullOrEmpty()

    if (filterLabel.isVisible)
      filterLabel.text = KafkaMessagesBundle.message("consumer.preset.filter", filter.type.title)
    if (filterKeyLabel.isVisible)
      filterKeyLabel.text = KafkaMessagesBundle.message("consumer.preset.key", filter.filterKey ?: "")
    if (filterValueLabel.isVisible)
      filterValueLabel.text = KafkaMessagesBundle.message("consumer.preset.value", filter.filterValue ?: "")
    if (filterHeaderKeyLabel.isVisible)
      filterHeaderKeyLabel.text = KafkaMessagesBundle.message("consumer.preset.header.key", filter.filterHeadKey ?: "")
    if (filterHeaderValueLabel.isVisible)
      filterHeaderValueLabel.text = KafkaMessagesBundle.message("consumer.preset.header.value", filter.filterHeadValue ?: "")

    return component
  }
}

class ConsumerPresets : Presets<StorageConsumerConfig>(KafkaConfigStorage.instance.consumerConfig, RunConsumerConfigCellRenderer())
