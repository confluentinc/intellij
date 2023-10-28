package com.jetbrains.bigdatatools.kafka.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.michaelbaranov.microba.calendar.DatePicker
import kotlinx.coroutines.*

class KafkaConsumerGroupChangeOffsetProcess(val project: Project, val dataManager: KafkaDataManager, val consumerGroup: String) {
  private val coroutineScope = dataManager.driver.coroutineScope
  private val startSpecificDate = DatePicker()
  private val startType = AtomicProperty(ConsumerStartType.NOW)
  private val topic = AtomicProperty(KafkaMessagesBundle.message("all.topics"))
  private val startOffset = AtomicProperty(0)

  fun showAndUpdate() {
    coroutineScope.launch {
      withBackgroundProgress(project, KafkaMessagesBundle.message("task.change.offset"), cancellable = true) {
        try {
          internalChangeOffsetWithDialog()
        }
        catch (t: Throwable) {
          withContext(Dispatchers.EDT) {
            RfsNotificationUtils.showExceptionMessage(project, t)
          }
        }
      }
    }
  }

  private suspend fun internalChangeOffsetWithDialog() {
    val consumerGroupOffset = withContext(Dispatchers.IO) {
      dataManager.loadConsumerGroupOffset(consumerGroup)
    }


    val topics: List<String> = consumerGroupOffset.map { it.topic }.distinct()
    val allTopicName = KafkaMessagesBundle.message("all.topics")
    val allSelectableTopics = if (topics.size <= 1)
      topics
    else
      listOf(allTopicName) + topics

    val res = createAndShowDialog(allSelectableTopics)
    if (res)
      return

    val selectedNames = if (topic.get() == allTopicName) {
      topics
    }
    else {
      listOf(topic.get())
    }

    val topicInfos = selectedNames.map {
      coroutineScope.async {
        dataManager.loadTopicInfo(it)
      }
    }.awaitAll()
    val partitions = topicInfos.flatMap { it.partitionList }
    val startWith = ConsumerStartWith(type = startType.get(),
                                      time = startSpecificDate.date.time,
                                      offset = startOffset.get().toLong(),
                                      consumerGroup = null)

    val partitionsWithNewOffsets = KafkaOffsetUtils.calculateOffsets(partitions, startWith, dataManager)
    dataManager.resetOffsets(consumerGroup, partitionsWithNewOffsets)
  }

  private suspend fun createAndShowDialog(allSelectableTopics: List<String>) = withContext(Dispatchers.EDT) {
    val builder = DialogBuilder()
    builder.addOkAction()
    builder.addCancelAction()
    builder.title(KafkaMessagesBundle.message("action.kafka.ResetOffsetsAction.text"))
    builder.centerPanel(createPanel(allSelectableTopics))
    !builder.showAndGet()
  }

  private fun createPanel(allSelectableTopics: List<String>): DialogPanel {
    val centralPanel = panel {
      row(KafkaMessagesBundle.message("consumer.record.topic")) {
        comboBox(allSelectableTopics).align(AlignX.FILL).resizableColumn().bindItem(topic)
      }
      row(KafkaMessagesBundle.message("consumer.group.dialog.change.offset.strategy.label")) {
        comboBox(ConsumerStartType.entries - ConsumerStartType.CONSUMER_GROUP,
                 CustomListCellRenderer<ConsumerStartType> { it.title })
          .bindItem(startType).align(AlignX.FILL).resizableColumn()
      }
      row(KafkaMessagesBundle.message("consumer.group.dialog.change.offset.datetime.label")) {
        cell(startSpecificDate).align(AlignX.FILL).resizableColumn()
      }.visibleIf(startType.equalsTo(ConsumerStartType.SPECIFIC_DATE))
      row(KafkaMessagesBundle.message("consumer.group.dialog.change.offset.offset.label")) {
        intTextField().bindIntText(startOffset).align(AlignX.FILL).resizableColumn()
      }.visibleIf(startType.equalsTo(ConsumerStartType.OFFSET).or(startType.equalsTo(ConsumerStartType.LATEST_OFFSET_MINUS_X)))
    }
    return centralPanel
  }
}

