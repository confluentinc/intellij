package io.confluent.kafka.core.settings.fields

import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.ui.ComboBoxWidePopup
import io.confluent.kafka.core.ui.CustomListCellRenderer
import javax.swing.DefaultComboBoxModel
import kotlin.reflect.KMutableProperty1

/**
 * @param titleResolver is optional projection item -> its title.
 */
class NullableComboBoxField<D : ConnectionData, E : Any?>(private val prop: KMutableProperty1<D, E?>,
                                                          key: ModificationKey,
                                                          private val initSettings: D,
                                                          items: Array<E>,
                                                          private val nullElement: E,
                                                          titleResolver: ((E) -> String?)? = null) : WrappedNamedComponent<D>(key) {
  private val comboBoxModel = DefaultComboBoxModel(items)

  private val comboBox = ComboBoxWidePopup(comboBoxModel, nullElement)

  init {
    if (titleResolver != null) {
      comboBox.renderer = CustomListCellRenderer(titleResolver)
    }

    val targetItem = prop.get(initSettings) ?: nullElement

    if (comboBoxModel.getIndexOf(targetItem) != -1) {
      comboBox.selectedItem = targetItem
    }
  }

  fun setItems(items: Array<E>, titleResolver: ((E) -> String?)? = null) {
    val selectedItem = if (comboBoxModel.size == 0)
      prop.get(initSettings)
    else
      getValue() ?: nullElement

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

  override fun isModified(conn: D): Boolean = (prop.get(conn) ?: nullElement) != getValue()

  override fun apply(conn: D) = prop.set(conn, getValue()?.takeIf { it != nullElement })
}