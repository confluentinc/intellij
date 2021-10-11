package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.settings.ConfigChangeListener
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerFilterType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerLimitType
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import com.jetbrains.bigdatatools.ui.MigPanel
import java.awt.Component
import java.text.SimpleDateFormat
import javax.swing.*

class RunConsumerConfigCellRenderer : ListCellRenderer<RunConsumerConfig> {

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

  override fun getListCellRendererComponent(list: JList<out RunConsumerConfig>,
                                            value: RunConsumerConfig,
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

    @Suppress("HardCodedStringLiteral")
    topicLabel.text = value.topic.ifEmpty { "No topic" }

    @Suppress("HardCodedStringLiteral")
    keyLabel.text = "Key: ${value.keyType.value}"
    @Suppress("HardCodedStringLiteral")
    valueLabel.text = "Value: ${value.valueType.value}"

    startFromLabel.isVisible = false

    if (value.limit.type == ConsumerLimitType.NONE) {
      limitLabel.isVisible = false
    }
    else {
      limitLabel.isVisible = true
      limitLabel.text = "Limit: ${value.limit.type.title} ${
        if (value.limit.type == ConsumerLimitType.DATE) dateFormat.format(value.limit.time) else value.limit.value
      }"
    }

    val filterVisible = value.filter.type != ConsumerFilterType.NONE

    filterLabel.isVisible = filterVisible
    filterKeyLabel.isVisible = filterVisible && !value.filter.filterKey.isNullOrEmpty()
    filterValueLabel.isVisible = filterVisible && !value.filter.filterValue.isNullOrEmpty()
    filterHeaderKeyLabel.isVisible = filterVisible && !value.filter.filterHeadKey.isNullOrEmpty()
    filterHeaderValueLabel.isVisible = filterVisible && !value.filter.filterHeadValue.isNullOrEmpty()

    if (filterLabel.isVisible)
      filterLabel.text = "Filter: ${value.filter.type.title}"
    if (filterKeyLabel.isVisible)
      filterKeyLabel.text = "Key: ${value.filter.filterKey}"
    if (filterValueLabel.isVisible)
      filterValueLabel.text = "Value: ${value.filter.filterValue}"
    if (filterHeaderKeyLabel.isVisible)
      filterHeaderKeyLabel.text = "Header key: ${value.filter.filterHeadKey}"
    if (filterHeaderValueLabel.isVisible)
      filterHeaderValueLabel.text = "Header value: ${value.filter.filterHeadValue}"

    return component
  }
}

class ConsumerPresets : ConfigChangeListener<RunConsumerConfig>, Disposable {
  private val model = DefaultListModel<RunConsumerConfig>()
  private val presetsPanel = JBList(model).apply {
    cellRenderer = RunConsumerConfigCellRenderer()
  }

  var onApply: ((RunConsumerConfig) -> Unit)? = null

  val component = ToolbarDecorator.createDecorator(presetsPanel)
    .setMoveDownAction(null)
    .setMoveUpAction(null)
    .setEditAction {
      presetsPanel.selectedValue?.let { onApply?.invoke(it) }
    }
    .setRemoveAction {
      presetsPanel.selectedValue?.let { KafkaConfigStorage.instance.removeConsumerConfig(it) }
    }.createPanel().apply {
      border = JBUI.Borders.empty()
    }

  init {
    model.addAll(KafkaConfigStorage.instance.loadConsumerConfigs())
    KafkaConfigStorage.instance.addConsumerChangeListener(this)
  }

  override fun dispose() {
    KafkaConfigStorage.instance.removeConsumerChangeListener(this)
  }

  override fun configAdded(config: RunConsumerConfig) = model.addElement(config)
  override fun configRemoved(config: RunConsumerConfig) {
    model.removeElement(config)
  }
}