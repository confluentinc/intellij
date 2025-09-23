package io.confluent.kafka.core.settings.fields

import com.intellij.ui.components.JBLabel
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData

abstract class WrappedNamedComponent<D : ConnectionData>(key: ModificationKey) : WrappedComponent<D>(key) {

  val labelComponent: JBLabel = JBLabel(key.label)

  override var isVisible: Boolean
    get() = super.isVisible
    set(value) {
      super.isVisible = value
      labelComponent.isVisible = value
    }
}