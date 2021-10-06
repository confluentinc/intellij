package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.kafka.common.editor.PropertiesTable
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.ui.EmptyCell
import com.jetbrains.bigdatatools.ui.MigPanel
import com.jetbrains.bigdatatools.util.SizeUtils
import com.jetbrains.bigdatatools.util.TimeUtils
import net.miginfocom.layout.LC
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.io.Serializable
import java.nio.charset.StandardCharsets
import javax.swing.JLabel
import javax.swing.JTextArea
import javax.swing.JTextField

class ConsumerRecordDetails {
  private val topicField = JTextField(10)
  private val keyField = JTextField(10)
  private val valueField = JTextArea().apply { wrapStyleWord = true }
  private val headers = PropertiesTable(emptyList())

  private val partition = JTextField(10)
  private val offset = JTextField(10)
  private val timestamp = JTextField(10)
  private val timestampType = JTextField(10)
  private val keySize = JTextField(10)
  private val valueSize = JTextField(10)

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
        timestamp.text = TimeUtils.unixTimeToString(value.timestamp())
        timestampType.text = value.timestampType().toString()
        keySize.text = SizeUtils.toString(value.serializedKeySize())
        valueSize.text = SizeUtils.toString(value.serializedValueSize())

        val headerProperties = value.headers().map { Property(it.key(), String(it.value(), StandardCharsets.UTF_8)) }
        headers.properties = headerProperties.toMutableList()
      }
    }

  val component = MigPanel(LC().insets("10").fillX().hideMode(3)).apply {
    row(KafkaMessagesBundle.message("consumer.record.topic"), topicField)
    row(KafkaMessagesBundle.message("consumer.record.key"), keyField)
    row(KafkaMessagesBundle.message("consumer.record.value"))
    block(valueField)

    row(KafkaMessagesBundle.message("consumer.record.partition"), partition)
    row(KafkaMessagesBundle.message("consumer.record.offset"), offset)
    row(KafkaMessagesBundle.message("consumer.record.timestamp"), timestamp)
    row(EmptyCell(), timestampType)
    row(KafkaMessagesBundle.message("consumer.record.keysize"), keySize)
    row(KafkaMessagesBundle.message("consumer.record.valuesize"), valueSize)

    add(JLabel(KafkaMessagesBundle.message("consumer.record.headers")), UiUtil.wrap)
    block(headers.getComponent())
  }
}