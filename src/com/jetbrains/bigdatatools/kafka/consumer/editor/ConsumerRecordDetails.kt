package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.kafka.producer.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.io.Serializable
import javax.swing.*

class ConsumerRecordDetails {
  var record: ConsumerRecord<Serializable, Serializable>? = null
    set(value) {
      field = value

      if (value == null) {
        keyField.text = ""
        valueField.text = ""
      }
      else {
        keyField.text = value.key().toString()
        valueField.text = value.value().toString()
      }
    }

  val keyField = JTextField()
  val valueField = JTextArea().apply {
    wrapStyleWord = true
  }
  val headers = PropertiesTable("")
  val metadata = PropertiesTable("")

  val component = JPanel(null).apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    // add(JLabel(KafkaMessagesBundle.message("details.data")))
    add(keyField)
    add(valueField)
    add(JLabel(KafkaMessagesBundle.message("details.headers")))
    add(headers.getComponent())
    add(JLabel(KafkaMessagesBundle.message("details.metadata")))
    add(metadata.getComponent())
  }
}