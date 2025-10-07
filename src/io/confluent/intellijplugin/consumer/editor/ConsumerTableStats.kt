package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.actionSystem.DefaultActionGroup
import io.confluent.intellijplugin.core.ui.CustomComponentActionImpl
import io.confluent.intellijplugin.core.ui.ToolbarGreyLabelActionImpl
import io.confluent.intellijplugin.core.util.SizeUtils
import io.confluent.intellijplugin.core.util.TimeUtils
import io.confluent.intellijplugin.core.util.ToolbarUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JLabel

class ConsumerTableStats {
    private var totalMessageCount: Long = 0
    private var totalMessageSize: Long = 0
    private var totalPollCount: Long = 0
    private var totalPollTime: Long = 0
    private var startTime: Long = System.currentTimeMillis()

    private val totalTimeLabel = JLabel("?")

    private val messagesTotalCountLabel = JLabel("?")
    private val messagesTotalSizeLabel = JLabel("?")
    private val messagesPerSecondLabel = JLabel("?")

    private val totalFetchLabel = JLabel("?")
    private val averageFetchPerSecLabel = JLabel("?")

    @Suppress("DialogTitleCapitalization") // This is a special toolbar and this items should not be capitalized.
    val group = DefaultActionGroup().apply {
        add(ToolbarGreyLabelActionImpl(KafkaMessagesBundle.message("table.stats.count")))
        add(CustomComponentActionImpl(messagesTotalCountLabel))
        add(ToolbarGreyLabelActionImpl(KafkaMessagesBundle.message("table.stats.size")))
        add(CustomComponentActionImpl(messagesTotalSizeLabel))
        add(ToolbarGreyLabelActionImpl(KafkaMessagesBundle.message("table.stats.count.per.sec")))
        add(CustomComponentActionImpl(messagesPerSecondLabel))
        add(ToolbarGreyLabelActionImpl(KafkaMessagesBundle.message("table.stats.count.updates")))
        add(CustomComponentActionImpl(totalFetchLabel))
        add(ToolbarGreyLabelActionImpl(KafkaMessagesBundle.message("table.stats.count.updates.per.sec")))
        add(CustomComponentActionImpl(averageFetchPerSecLabel))
        add(ToolbarGreyLabelActionImpl(KafkaMessagesBundle.message("table.stats.elapsed")))
        add(CustomComponentActionImpl(totalTimeLabel))
    }

    val toolbar = ToolbarUtils.createActionToolbar("BDTCollapsiblePanel", group, horizontal = true)

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
            record.keySize + record.valueSize + record.headers.sumOf {
                (it.name?.length ?: 0) * 4 + (it.value?.length ?: 0) * 4
            }
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