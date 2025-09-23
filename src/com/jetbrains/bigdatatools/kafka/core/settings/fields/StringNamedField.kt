package io.confluent.kafka.core.settings.fields

import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import kotlin.reflect.KMutableProperty1

open class StringNamedField<D : ConnectionData>(prop: KMutableProperty1<D, String>,
                                                key: ModificationKey,
                                                initSettings: D,
                                                columns: Int = 1) : WrappedNamedField<D, String>(prop, key, initSettings, columns) {
  override fun apply(conn: D) {
    prop.set(conn, field.text)
  }
}