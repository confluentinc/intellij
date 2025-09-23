package io.confluent.kafka.core.settings.fields

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.fields.ExtendableTextField
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty1

abstract class WrappedNamedField<D : ConnectionData, T>(prop: KMutableProperty1<D, T>,
                                                        key: ModificationKey,
                                                        initSettings: D, columns: Int = 1) : WrappedTextComponent<D, T>(prop, key) {
  protected open val field = ExtendableTextField(prop.get(initSettings).toString(), columns)

  var emptyText: String
    get() = this.field.emptyText.text
    set(value) {
      this.field.emptyText.text = value
    }

  fun setValue(s: String) {
    field.text = s
  }

  @NlsContexts.LinkLabel
  override fun getValue(): String = field.text
  override fun getComponent(): JComponent = field
  override fun getTextComponent(): ExtendableTextField = field
}