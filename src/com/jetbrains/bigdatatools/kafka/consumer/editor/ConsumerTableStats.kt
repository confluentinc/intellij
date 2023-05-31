package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.jetbrains.bigdatatools.common.util.SizeUtils
import com.jetbrains.bigdatatools.common.util.TimeUtils
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel

class ConsumerTableStats {
  private var totalMessageCount: Long = 0
  private var totalMessageSize: Long = 0
  private var totalPollCount: Long = 0
  private var totalPollTime: Long = 0
  private var startTime: Long = System.currentTimeMillis()

  private val totalTimeLabel = JLabel()

  private val messagesTotalCountLabel = JLabel()
  private val messagesTotalSizeLabel = JLabel()
  private val messagesPerSecondLabel = JLabel()

  private val totalFetchLabel = JLabel()
  private val averageFetchPerSecLabel = JLabel()

  val component = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    border = IdeBorderFactory.createBorder(SideBorder.TOP)
    add(JLabel(KafkaMessagesBundle.message("table.stats.count")).apply { isEnabled = false }); add(messagesTotalCountLabel)
    add(JLabel(KafkaMessagesBundle.message("table.stats.size")).apply { isEnabled = false }); add(messagesTotalSizeLabel)
    add(JLabel(KafkaMessagesBundle.message("table.stats.count.per.sec")).apply { isEnabled = false }); add(messagesPerSecondLabel)
    add(JLabel(KafkaMessagesBundle.message("table.stats.count.updates")).apply { isEnabled = false }); add(totalFetchLabel)
    add(JLabel(KafkaMessagesBundle.message("table.stats.count.updates.per.sec")).apply { isEnabled = false }); add(averageFetchPerSecLabel)
    add(JLabel(KafkaMessagesBundle.message("table.stats.elapsed")).apply { isEnabled = false }); add(totalTimeLabel)
  }

  fun start() {
    totalMessageCount = 0
    totalMessageSize = 0
    totalPollCount = 0
    totalPollTime = 0
    startTime = System.currentTimeMillis()
  }

  fun addRecordsBatch(pollTime: Long, elements: List<KafkaRecord>) {
    totalPollTime += pollTime
    totalPollCount += 1
    totalMessageCount += elements.size
    totalMessageSize += elements.sumOf { record ->
      record.keySize + record.valueSize + record.headers.sumOf { (it.name?.length ?: 0) * 4 + (it.value?.length ?: 0) * 4 }
    }
    updateUi()
  }

  private fun updateUi() {
    val elapsedTime = System.currentTimeMillis() - startTime
    totalTimeLabel.text = TimeUtils.intervalAsString(elapsedTime, withMs = false)
    messagesTotalCountLabel.text = totalMessageCount.toString()
    messagesTotalSizeLabel.text = SizeUtils.toString(totalMessageSize)
    messagesPerSecondLabel.text = "%.2f".format(totalMessageCount.toDouble() / elapsedTime * 1000)
    totalFetchLabel.text = totalPollCount.toString()
    averageFetchPerSecLabel.text = "%.2f".format(totalPollCount.toDouble() / totalPollTime * 1000)
  }
}