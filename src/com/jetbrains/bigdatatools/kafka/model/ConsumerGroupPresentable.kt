package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.table.renderers.DataRenderingUtil
import org.apache.kafka.common.ConsumerGroupState
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class ConsumerGroupPresentable(val state: ConsumerGroupState,
                                    val consumerGroupName: String,
                                    val numConsumers: Int,
                                    val numTopics: Int,
                                    val numTopicPartitions: Int) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<ConsumerGroupPresentable, *>> by lazy {
      ConsumerGroupPresentable::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}