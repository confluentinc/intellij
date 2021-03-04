package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.table.renderers.DataRenderingUtil
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

class TopicConfigPresentable(val name: String, val value: String, val defaultValue: String = "") : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<TopicConfigPresentable, *>> by lazy {
      TopicConfigPresentable::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}