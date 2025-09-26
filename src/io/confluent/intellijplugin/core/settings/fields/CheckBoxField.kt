package io.confluent.intellijplugin.core.settings.fields

import com.intellij.ui.components.JBCheckBox
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import javax.swing.SwingConstants
import kotlin.reflect.KMutableProperty1

class CheckBoxField<D : ConnectionData>(private val prop: KMutableProperty1<D, Boolean>,
                                        key: ModificationKey,
                                        initSettings: D, doNotCreateLabel: Boolean = false) : WrappedComponent<D>(key) {

  val checkBoxField = if (doNotCreateLabel)
    JBCheckBox()
  else
    JBCheckBox(key.label).apply {
      horizontalTextPosition = SwingConstants.RIGHT
      isSelected = prop.get(initSettings) == true
    }

  override fun apply(conn: D) = prop.set(conn, getValue())

  override fun getValue(): Boolean = checkBoxField.isSelected

  override fun getComponent(): JBCheckBox = checkBoxField

  override fun isModified(conn: D): Boolean = prop.get(conn) != getValue()
}