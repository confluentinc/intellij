package com.jetbrains.bigdatatools.kafka.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.bigdatatools.kafka.common.models.FieldType

class KafkaUsagesCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  companion object {
    private val GROUP = EventLogGroup("bigdatatools.kafka", 2)

    val openProducerEvent = GROUP.registerEvent("open.producer")
    val openConsumerEvent = GROUP.registerEvent("open.consumer")
    val openProducerAndConsumerEvent = GROUP.registerEvent("open.producer.and.consumer")

    val topicCreatedEvent = GROUP.registerEvent("topic.created")
    val topicDeletedEvent = GROUP.registerEvent("topic.deleted")

    val producedKeyValue = GROUP.registerEvent("produced.keyvalue",
                                               EventFields.Enum<FieldType>("key_type"),
                                               EventFields.Enum<FieldType>("value_type"))

    val consumedKeyValue = GROUP.registerEvent("consumed.keyvalue",
                                               EventFields.Enum<FieldType>("key_type"),
                                               EventFields.Enum<FieldType>("value_type"),
                                               RoundedIntEventField("count"))
  }
}