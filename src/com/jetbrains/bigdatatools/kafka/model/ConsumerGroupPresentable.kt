package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import org.apache.kafka.common.ConsumerGroupState
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class ConsumerGroupPresentable(val state: ConsumerGroupState,
                                    val consumerGroup: String,
                                    val consumers: Int,
                                    val topics: Int,
                                    val partitions: Int) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<ConsumerGroupPresentable, *>> by lazy {
      ConsumerGroupPresentable::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}