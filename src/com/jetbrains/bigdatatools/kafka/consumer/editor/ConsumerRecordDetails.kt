package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.kafka.producer.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.MigPanel
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.io.Serializable
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.JTextField

class ConsumerRecordDetails {
  var record: ConsumerRecord<Serializable, Serializable>? = null
    set(value) {
      field = value

      if (value == null) {
        keyField.text = ""
        valueField.text = ""
        topicField.text = ""

        partition.text = ""
        offset.text = ""
        timestamp.text = ""
        timestampType.text = ""
        keySize.text = ""
        valueSize.text = ""

        headers.clear()
      }
      else {
        topicField.text = value.topic()
        keyField.text = value.key()?.toString()
        valueField.text = value.value()?.toString()

        partition.text = value.partition().toString()
        offset.text = value.offset().toString()
        timestamp.text = value.timestamp().toString()
        timestampType.text = value.timestampType().toString()
        keySize.text = value.serializedKeySize().toString()
        valueSize.text = value.serializedValueSize().toString()

        val headerProperties = value.headers().map { Property(it.key(), String(it.value())) }
        headers.properties = headerProperties.toMutableList()
      }
    }

  private val topicField = JTextField()
  private val keyField = JTextField()
  private val valueField = JTextArea().apply { wrapStyleWord = true }
  private val headers = PropertiesTable("")

  private val partition = JTextField()
  private val offset = JTextField()
  private val timestamp = JTextField()
  private val timestampType = JTextField()
  private val keySize = JTextField()
  private val valueSize = JTextField()

  val component = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {
    // add(JLabel(KafkaMessagesBundle.message("details.data")))
    row(JLabel("Topic:"), topicField)
    row(JLabel("Key:"), keyField)
    add(JLabel("Value:"), UiUtil.wrap)
    block(valueField)

    row("Partition:", partition)
    row("Offset:", offset)
    row("Timestamp:", timestamp)
    row("", timestampType)
    row("Key size:", keySize)
    row("Value size:", valueSize)

    add(JLabel(KafkaMessagesBundle.message("details.headers")), UiUtil.wrap)
    block(headers.getComponent())
  }
}