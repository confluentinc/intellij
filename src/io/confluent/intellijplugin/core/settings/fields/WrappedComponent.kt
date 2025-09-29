package io.confluent.intellijplugin.core.settings.fields

import com.intellij.openapi.ui.ComponentValidator
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.getValidator
import io.confluent.intellijplugin.core.table.renderers.NoRendering
import javax.swing.JCheckBox
import javax.swing.JComponent

@NoRendering
abstract class WrappedComponent<D : ConnectionData>(val key: ModificationKey) {
  lateinit var connData: ConnectionData
  open var isVisible: Boolean
    get() = getComponent().isVisible
    set(value) {
      getComponent().isVisible = value
    }

  open var isGhost = false

  abstract fun getValue(): Any?
  abstract fun getComponent(): JComponent
  abstract fun apply(conn: D)
  abstract fun isModified(conn: D): Boolean
  open fun addIsPerProjectListeners(checkBox: JCheckBox) {}
  open fun init(conn: D) {}
  open fun getValidators(): List<ComponentValidator> = getComponent().let { component ->
    if (!component.isEnabled || !component.isVisible) emptyList() else component.getValidator()?.let { listOf(it) } ?: emptyList()
  }
}