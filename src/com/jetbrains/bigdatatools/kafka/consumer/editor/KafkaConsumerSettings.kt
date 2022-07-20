package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.ui.MigPanel
import org.apache.kafka.clients.consumer.ConsumerConfig
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JSeparator
import javax.swing.JTextField
import javax.swing.text.JTextComponent

class KafkaConsumerSettings {

  companion object {
    const val MAX_CONSUMER_RECORDS = "consumer.records.limit"
  }

  // Properties from org.apache.kafka.clients.consumer.ConsumerConfig
  private val propertiesFields = LinkedHashMap<String, JTextComponent>()

  // Our settings like "Display only last 100 records"
  private val settingsFields = LinkedHashMap<String, JTextComponent>()

  init {
    arrayOf(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
            ConsumerConfig.FETCH_MAX_BYTES_CONFIG,
            ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG).forEach {

      val textField = JTextField().apply {
        val defaults = ConsumerConfig.configDef().configKeys()[it]
        text = defaults?.defaultValue?.toString()
        @Suppress("HardCodedStringLiteral")
        toolTipText = defaults?.documentation
      }

      propertiesFields[it] = textField
    }

    settingsFields[MAX_CONSUMER_RECORDS] = JTextField().apply {
      toolTipText = KafkaMessagesBundle.message("consumer.records.limit.descr")
    }
  }

  fun applyConfig(config: StorageConsumerConfig) {
    val defaults = ConsumerConfig.configDef().configKeys()
    propertiesFields.forEach {
      it.value.text = defaults[it.key]?.defaultValue?.toString()
    }

    config.properties.forEach {
      propertiesFields[it.key]?.text = it.value
    }

    config.settings.forEach {
      settingsFields[it.key]?.text = it.value
    }
  }

  fun getProperties(): Map<String, String> {
    val defaults = ConsumerConfig.configDef().configKeys()
    return propertiesFields.filter {
      it.value.text != defaults[it.key]?.defaultValue?.toString() &&
      it.value.text.isNotBlank() &&
      it.value.text.toIntOrNull() != null
    }.mapValues { it.value.text }
  }

  fun getSettings(): Map<String, String> {
    return settingsFields.filter {
      it.value.text.isNotBlank() &&
      it.value.text.toIntOrNull() != null
    }.mapValues { it.value.text }
  }

  fun show() {
    val oldProperties = propertiesFields.mapValues { it.value.text }
    val oldSettings = settingsFields.mapValues { it.value.text }

    val panel = MigPanel().apply {
      settingsFields.forEach {
        row(JLabel(KafkaMessagesBundle.message(it.key)).apply {
          toolTipText = it.value.toolTipText
        }, it.value)
      }

      block(JSeparator())

      propertiesFields.forEach {
        row(JLabel(KafkaMessagesBundle.message(it.key)).apply {
          toolTipText = it.value.toolTipText
        }, it.value)
      }
    }

    DialogBuilder().apply {
      setTitle(KafkaMessagesBundle.message("settings.advanced"))
      setCenterPanel(JBScrollPane(panel).apply {
        border = BorderFactory.createEmptyBorder()
      })

      if (!showAndGet()) {
        propertiesFields.forEach { it.value.text = oldProperties[it.key] }
        settingsFields.forEach { it.value.text = oldSettings[it.key] }
      }
    }
  }
}