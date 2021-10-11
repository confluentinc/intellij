package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.openapi.Disposable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.jetbrains.bigdatatools.kafka.common.settings.ConfigChangeListener
import com.jetbrains.bigdatatools.kafka.common.settings.KafkaConfigStorage
import com.jetbrains.bigdatatools.kafka.producer.models.RunProducerConfig
import com.jetbrains.bigdatatools.ui.MigPanel
import java.awt.Component
import javax.swing.*

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

    @Suppress("HardCodedStringLiteral")
    topicLabel.text = "Topic: ${if (value?.topic.isNullOrEmpty()) "No topic" else value?.topic}"
    @Suppress("HardCodedStringLiteral")
    keyLabel.text = "Key [${value?.keyType?.value ?: "none"}]:  ${if (value?.key.isNullOrEmpty()) "No key" else value?.key}"
    @Suppress("HardCodedStringLiteral")
    valueLabel.text = "Value [${value?.valueType?.value ?: "none"}]:  ${if (value?.value.isNullOrEmpty()) "No key" else value?.value}"

    return component
  }
}

class ProducerPresets : ConfigChangeListener<RunProducerConfig>, Disposable {
  private val model = DefaultListModel<RunProducerConfig>()
  private val presetsPanel = JBList(model).apply {
    cellRenderer = RunProducerConfigCellRenderer()
  }

  var onApply: ((RunProducerConfig) -> Unit)? = null

  val component = ToolbarDecorator.createDecorator(presetsPanel)
    .setMoveDownAction(null)
    .setMoveUpAction(null)
    .setEditAction {
      presetsPanel.selectedValue?.let { onApply?.invoke(it) }
    }
    .setRemoveAction {
      presetsPanel.selectedValue?.let { KafkaConfigStorage.instance.removeProducerConfig(it) }
    }.createPanel().apply {
      border = JBUI.Borders.empty()
    }

  init {
    model.addAll(KafkaConfigStorage.instance.loadProducerConfigs())
    KafkaConfigStorage.instance.addProducerChangeListener(this)
  }

  override fun dispose() {
    KafkaConfigStorage.instance.removeProducerChangeListener(this)
  }

  override fun configAdded(config: RunProducerConfig) = model.addElement(config)
  override fun configRemoved(config: RunProducerConfig) {
    model.removeElement(config)
  }
}