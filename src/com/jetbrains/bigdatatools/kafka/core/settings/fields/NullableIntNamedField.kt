package io.confluent.kafka.core.settings.fields

import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty1

class NullableIntNamedField<D : ConnectionData>(prop: KMutableProperty1<D, Int?>, key: ModificationKey, initSettings: D)
  : WrappedTextComponent<D, Int?>(prop, key) {

  private val field : JTextField

  init {
    val value = prop.get(initSettings)
    field = if(value == null) JTextField() else JTextField(value.toString())
  }

  override fun getValue(): String = field.text
  override fun getComponent(): JComponent = field
  override fun getTextComponent(): JTextComponent = field
  override fun isModified(conn: D): Boolean = !(getTextComponent().text == "" && prop.get(conn) == null) && super.isModified(conn)

  override fun apply(conn: D) {
    prop.set(conn, getValue().toIntOrNull())
  }
}