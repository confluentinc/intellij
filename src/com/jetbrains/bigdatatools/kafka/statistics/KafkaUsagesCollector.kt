package com.jetbrains.bigdatatools.kafka.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType

object KafkaUsagesCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  private val GROUP = EventLogGroup("bigdatatools.kafka", 5)

  val topicClearEvent = GROUP.registerEvent("topic.clear")
  val partitionsClearEvent = GROUP.registerEvent("partitions.clear")

  val openProducerEvent = GROUP.registerEvent("open.producer")
  val openConsumerEvent = GROUP.registerEvent("open.consumer")

  val topicCreatedEvent = GROUP.registerEvent("topic.created")
  val topicDeletedEvent = GROUP.registerEvent("topic.deleted")

  val producedKeyValue = GROUP.registerEvent("produced.keyvalue",
                                             EventFields.Enum<KafkaFieldType>("key_type"),
                                             EventFields.Enum<KafkaFieldType>("value_type"))

  val consumedKeyValue = GROUP.registerEvent("consumed.keyvalue",
                                             EventFields.Enum<KafkaFieldType>("key_type"),
                                             EventFields.Enum<KafkaFieldType>("value_type"),
                                             RoundedIntEventField("count"))
}