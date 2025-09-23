package io.confluent.kafka.core.settings.fields

import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty1

abstract class WrappedTextComponent<D : ConnectionData, T>(protected val prop: KMutableProperty1<D, T>,
                                                           key: ModificationKey
) : WrappedNamedComponent<D>(key) {
  abstract fun getTextComponent(): JTextComponent

  override fun isModified(conn: D): Boolean = prop.get(conn)?.toString().orEmpty() != getTextComponent().text
}