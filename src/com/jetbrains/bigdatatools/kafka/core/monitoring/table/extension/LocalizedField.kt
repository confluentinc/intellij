package com.jetbrains.bigdatatools.kafka.core.monitoring.table.extension

import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.RemoteInfo
import org.jetbrains.annotations.Nls
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

abstract class LocalizedField<T : RemoteInfo>(val field: KProperty1<T, *>, val i18Key: String?) {
  val name: String
    get() = this.field.name

  abstract fun getLocalizedName(): @Nls String

  fun getAnnotations() = (field.annotations + (field.javaField?.annotations ?: emptyArray())).toTypedArray()
}