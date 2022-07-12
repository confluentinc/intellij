package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.awt.FlowLayout
import java.util.*
import javax.swing.JLabel
import javax.swing.JPanel

class ConsumerTableStats {
  val total = JLabel()
  val visible = JLabel()

  val component = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    border = IdeBorderFactory.createBorder(SideBorder.TOP)
    add(JLabel("total: "))
    add(total)
    add(JLabel("visible: "))
    add(visible)
    add(JLabel("speed: "))
    add(visible)
    add(JLabel("memory: "))
    add(visible)
  }

  var totalSize = 0L
  var speed = 0L
  var processedCount = 0

  private val lastRecordSizes = LinkedList<Pair<Long, Int>>()

  fun addRecord(record: ConsumerRecord<Any, Any>) {
    val size = record.serializedKeySize() + record.serializedValueSize()
    totalSize += size
    lastRecordSizes += Pair(System.currentTimeMillis(), size)
    if (lastRecordSizes.size > 50) {
      lastRecordSizes.removeFirst()
    }

    processedCount++
  }
}