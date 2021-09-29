package com.jetbrains.bigdatatools.kafka.producer.editor.renders

import com.intellij.ui.components.JBLabel
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerResultMessage
import java.awt.BorderLayout
import java.awt.Component
import java.text.SimpleDateFormat
import javax.swing.*

class ProducerOutputRender : JPanel(null), ListCellRenderer<ProducerResultMessage> {

  companion object {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  }

  private val time = JBLabel()
  private val duration = JLabel()
  private val body = JLabel()
  private val offset = JLabel()

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(JPanel(BorderLayout()).apply {
      add(time, BorderLayout.LINE_START)
      add(duration, BorderLayout.LINE_END)
    })
    add(body)
    add(offset)
  }

  override fun getListCellRendererComponent(list: JList<out ProducerResultMessage>,
                                            value: ProducerResultMessage,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {

    val formattedTimeStamp = dateFormat.format(value.timestamp)

    time.text = "Date: $formattedTimeStamp"
    offset.text = "Offset: ${value.offset}"
    body.text = value.key
    duration.text = "Duration: ${value.duration}ms"
    return this
  }
}