package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.jetbrains.bigdatatools.common.table.MaterialTable
import com.jetbrains.bigdatatools.common.util.SizeUtils
import com.jetbrains.bigdatatools.kafka.common.editor.ListTableModel
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.awt.FlowLayout
import java.util.*
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.RowSorterEvent
import javax.swing.event.TableModelEvent

class ConsumerTableStats {
  private val total = JLabel()
  private val visible = JLabel()
  private val speed = JLabel()
  private val memory = JLabel()

  val component = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    border = IdeBorderFactory.createBorder(SideBorder.TOP)
    add(JLabel(KafkaMessagesBundle.message("table.stats.total")).apply { isEnabled = false }); add(total)
    add(JLabel(KafkaMessagesBundle.message("table.stats.visible")).apply { isEnabled = false }); add(visible)
    add(JLabel(KafkaMessagesBundle.message("table.stats.speed")).apply { isEnabled = false }); add(speed)
    add(JLabel(KafkaMessagesBundle.message("table.stats.memory")).apply { isEnabled = false }); add(memory)
  }

  private val lastRecordSizes = LinkedList<Pair<Long, Int>>()

  fun setModel(table: MaterialTable, model: ListTableModel<ConsumerOutputRow>) {

    table.rowSorter.addRowSorterListener { e ->
      if (e.type == RowSorterEvent.Type.SORTED) {
        visible.text = table.rowCount.toString()
      }
    }

    model.addTableModelListener { e ->
      total.text = model.rowCount.toString()
      visible.text = table.rowCount.toString()

      @Suppress("HardCodedStringLiteral")
      memory.text = SizeUtils.toString(model.elements().sumOf {
        val record = it.record.getOrNull() ?: return@sumOf 0
        record.serializedValueSize() + record.serializedKeySize()
      })

      if (e.type == TableModelEvent.INSERT) {
        for (i in e.firstRow until e.lastRow) {
          model.getValueAt(i)?.let { it.record.getOrNull()?.let { record -> addRecord(record) } }
        }
      }
    }
  }

  fun addRecord(record: ConsumerRecord<Any, Any>) {
    val size = record.serializedKeySize() + record.serializedValueSize()
    lastRecordSizes += Pair(System.currentTimeMillis(), size)
    if (lastRecordSizes.size > RECORDS_LIST_SPEED_SIZE) {
      lastRecordSizes.removeFirst()
    }

    val lastRecordsTime = (lastRecordSizes.last.first - lastRecordSizes.first.first) / 1000.0
    val lastRecordsSize = lastRecordSizes.sumOf { it.second }

    @Suppress("HardCodedStringLiteral")
    speed.text = SizeUtils.toString((lastRecordsSize / (lastRecordsTime * 60.0)).toInt())
  }

  companion object {
    private const val RECORDS_LIST_SPEED_SIZE = 50
  }
}