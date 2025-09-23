package io.confluent.kafka.core.settings.fields

import com.intellij.ui.components.fields.ExtendableTextField
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import kotlin.reflect.KMutableProperty1


class StringNonRequiredField<D : ConnectionData>(prop: KMutableProperty1<D, String?>,
                                                 key: ModificationKey,
                                                 initSettings: D, columns: Int = 1) : WrappedNamedField<D, String?>(prop, key,
                                                                                                                    initSettings) {
  @Suppress("HardCodedStringLiteral")
  override val field = ExtendableTextField(prop.get(initSettings), columns)

  override fun apply(conn: D) {
    prop.set(conn, field.text.ifBlank { null })
  }
}