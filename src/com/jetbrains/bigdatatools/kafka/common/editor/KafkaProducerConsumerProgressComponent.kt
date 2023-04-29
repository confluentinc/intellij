package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JComponent

class KafkaProducerConsumerProgressComponent {
  private val dateFormat = SimpleDateFormat("HH:mm:ss")

  var timestamp = 0L
  private val timestampLabel = JBLabel()
  private lateinit var timestampCell: Cell<JComponent>


  fun initCell(row: Row, bottomWidthGroup: String) {
    timestampCell = row.cell(timestampLabel).widthGroup(bottomWidthGroup).comment(
      KafkaMessagesBundle.message("consumer.last.update.label.comment"))
    timestampCell.visible(false)
  }

  fun onUpdate() {
    timestamp = System.currentTimeMillis()
    timestampLabel.text = dateFormat.format(Date(timestamp))
  }

  fun onError() {
    timestampLabel.icon = null
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
    timestampCell.visible(true)
  }
}