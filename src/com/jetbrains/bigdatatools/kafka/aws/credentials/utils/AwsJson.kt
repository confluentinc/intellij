package com.jetbrains.bigdatatools.kafka.aws.credentials.utils

import com.intellij.credentialStore.OneTimeString
import com.jetbrains.bigdatatools.kafka.core.serializer.JsonDeserializeException
import com.jetbrains.bigdatatools.kafka.core.serializer.JsonSerializeException
import com.jetbrains.bigdatatools.kafka.core.serializer.OneTimeStringAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant


/**
 * Object, which implements methods for work with Json
 */
object AwsJson {
  private val moshi: Moshi = Moshi.Builder()
    .add(OneTimeString::class.java, OneTimeStringAdapter)
    .add(Instant::class.java, CliCompatibleInstantDeserializer().nullSafe())
    .add(KotlinJsonAdapterFactory())
    .build()

  fun toJson(value: Any, pretty: Boolean = false, indent: String = "  "): String {
    var adapter = moshi.adapter(Any::class.java)

    if (pretty) {
      adapter = adapter.indent(indent)
      adapter = adapter.serializeNulls()
    }
    return adapter.toJson(value) ?: throw JsonSerializeException(value.toString())
  }

  fun <T> fromJsonToClass(jsonString: String, clazz: Class<T>): T =
    moshi.adapter(clazz).fromJson(jsonString) ?: throw JsonDeserializeException(jsonString, clazz::class.toString())


  @Suppress("unused")
  fun <T> fromJsonToClassMap(jsonString: String, clazz: Class<T>): Map<String, T> {
    val mapObj = moshi.adapter(Map::class.java).fromJson(jsonString)
                 ?: throw JsonDeserializeException(jsonString, List::class.toString())

    return mapObj.entries.associate { (k, v) -> k.toString() to fromValueObjToClass(v, clazz) }
  }


  private fun <T> fromValueObjToClass(value: Any?, clazz: Class<T>): T =
    moshi.adapter(clazz).fromJsonValue(value) ?: throw JsonDeserializeException(value.toString(), clazz::class.toString())


}