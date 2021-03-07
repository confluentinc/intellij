package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.table.renderers.DataRenderingUtil
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

class TopicConfig(val name: String, val value: String, val defaultValue: String = "") : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<TopicConfig, *>> by lazy {
      TopicConfig::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}