package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import org.apache.kafka.common.ConsumerGroupState
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class ConsumerGroupPresentable(val state: ConsumerGroupState,
                                    val consumerGroup: String,
                                    @field:LoadingRendering(rightAligned = true)
                                    val consumers: Int? = null,
                                    @field:LoadingRendering(rightAligned = true)
                                    val topics: Int? = null,
                                    @field:LoadingRendering
                                    val partitions: Int? = null) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<ConsumerGroupPresentable, *>> by lazy {
      ConsumerGroupPresentable::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }

    val localizedField: List<KafkaLocalizedField<ConsumerGroupPresentable>> by lazy {
      listOf()
    }
  }
}