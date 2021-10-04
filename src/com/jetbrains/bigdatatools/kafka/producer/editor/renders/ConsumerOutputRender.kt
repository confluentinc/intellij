package com.jetbrains.bigdatatools.kafka.producer.editor.renders

import com.intellij.ui.components.JBLabel
import com.jetbrains.bigdatatools.util.toPresentableText
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.awt.BorderLayout
import java.awt.Component
import java.io.Serializable
import java.text.SimpleDateFormat
import javax.swing.*

class ConsumerOutputRender : JPanel(null), ListCellRenderer<Result<ConsumerRecord<Serializable, Serializable>>> {

  companion object {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  }

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

  override fun getListCellRendererComponent(list: JList<out Result<ConsumerRecord<Serializable, Serializable>>>,
                                            value: Result<ConsumerRecord<Serializable, Serializable>>,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    if (value.isSuccess) {
      val consumerRecord = value.getOrThrow()
      val formattedTimeStamp = dateFormat.format(consumerRecord.timestamp())
      time.text = "Date: $formattedTimeStamp"
      offset.text = "Offset: ${consumerRecord.offset()}"
      body.text = (consumerRecord.key() ?: "null").toString() + ": " + (consumerRecord.value() ?: "null")
    }
    else {
      body.text = value.exceptionOrNull()?.toPresentableText() ?: "Error"
    }

    return this
  }
}