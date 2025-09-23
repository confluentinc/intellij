package io.confluent.kafka.core.table.renderers

import javax.swing.table.DefaultTableCellRenderer

class CustomTableCellRenderer<T>(private val getText: (T) -> Any?) : DefaultTableCellRenderer() {
  override fun setValue(value: Any?) {
    @Suppress("UNCHECKED_CAST")
    val tValue = value as? T
    if (tValue != null) {
      super.setValue(getText(tValue))
    }
    else {
      super.setValue(value)
    }
  }
}