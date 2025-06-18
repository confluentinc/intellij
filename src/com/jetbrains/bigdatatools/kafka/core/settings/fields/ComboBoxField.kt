package com.jetbrains.bigdatatools.kafka.core.settings.fields

import com.intellij.openapi.ui.ComboBox
import com.jetbrains.bigdatatools.kafka.core.settings.ModificationKey
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.ui.CustomListCellRenderer
import javax.swing.DefaultComboBoxModel
import kotlin.reflect.KMutableProperty1

/**
 * @param titleResolver is optional projection item -> its title.
 */
class ComboBoxField<D : ConnectionData, E : Any>(private val prop: KMutableProperty1<D, E>,
                                                 key: ModificationKey,
                                                 initSettings: D,
                                                 items: Array<E>,
                                                 titleResolver: ((E) -> String?)? = null) : WrappedComponent<D>(key) {

  private val comboBoxModel = DefaultComboBoxModel(items)
  private val comboBox = ComboBox(comboBoxModel)

  init {
    if (titleResolver != null) {
      comboBox.renderer = CustomListCellRenderer(titleResolver)
    }

    val targetItem = prop.get(initSettings)

    if (comboBoxModel.getIndexOf(targetItem) != -1) {
      comboBox.selectedItem = targetItem
    }
  }

  fun setItems(items: Array<E>, titleResolver: ((E) -> String?)? = null) {
    val selectedItem = getValue()

    if (titleResolver != null) {
      comboBox.renderer = CustomListCellRenderer(titleResolver)
    }

    comboBoxModel.removeAllElements()
    comboBoxModel.addAll(items.toList())

    if (comboBoxModel.getIndexOf(selectedItem) != -1) {
      comboBox.selectedItem = selectedItem
    }
  }

  override fun getValue(): E = comboBox.item

  override fun getComponent() = comboBox

  override fun isModified(conn: D): Boolean = prop.get(conn) != getValue()

  override fun apply(conn: D) = prop.set(conn, getValue())
}