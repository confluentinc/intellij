package com.jetbrains.bigdatatools.kafka.producer.editor.renders

import com.intellij.ui.components.JBLabel
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.awt.BorderLayout
import java.awt.Component
import java.io.Serializable
import java.text.SimpleDateFormat
import javax.swing.*

class ConsumerOutputRender : JPanel(null), ListCellRenderer<ConsumerRecord<Serializable, Serializable>> {
  private val time = JBLabel()
  private val body = JLabel()
  private val offset = JLabel()

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(JPanel(BorderLayout()).apply {
      add(time, BorderLayout.LINE_START)
    })
    add(body)
    add(offset)
  }


  override fun getListCellRendererComponent(list: JList<out ConsumerRecord<Serializable, Serializable>>,
                                            value: ConsumerRecord<Serializable, Serializable>,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val formattedTimeStamp = dateFormat.format(value.timestamp())
    time.text = "Date: $formattedTimeStamp"
    offset.text = "Offset: ${value.offset()}"
    body.text = value.key().toString() + ": " + value.value()
    return this
  }
}