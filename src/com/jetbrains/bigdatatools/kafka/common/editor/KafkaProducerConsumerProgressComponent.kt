package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Row
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.text.SimpleDateFormat
import java.util.*

class KafkaProducerConsumerProgressComponent {
  private val dateFormat = SimpleDateFormat("HH:mm:ss")

  var timestamp = 0L
  private val timestampLabel = JBLabel()
  fun initCell(row: Row, bottomWidthGroup: String) {
    row.cell(timestampLabel).widthGroup(bottomWidthGroup)
  }

  fun onUpdate() {
    timestamp = System.currentTimeMillis()
    timestampLabel.text = KafkaMessagesBundle.message("consumer.last.update.label.comment", dateFormat.format(Date(timestamp)))
  }

  fun onValidationError() {
    timestampLabel.icon = AllIcons.General.Error
    timestampLabel.text = KafkaMessagesBundle.message("kafka.validation.error.label")
  }

  fun onError() {
    timestampLabel.icon = null
    timestampLabel.text = KafkaMessagesBundle.message("consumer.last.update.label.error")
  }

  fun onStop() {
    timestampLabel.icon = null
    if (timestamp <= 0) {
      timestampLabel.text = KafkaMessagesBundle.message("consumer.last.update.label.canceled")
    }
  }

  fun onStart() {
    timestamp = 0
    timestampLabel.icon = AnimatedIcon.Default.INSTANCE
    timestampLabel.text = KafkaMessagesBundle.message("consumer.last.update.label.initializing")
  }
}