package io.confluent.intellijplugin.core.settings.fields

import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.components.RenderableEntity
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.ui.ComboBoxWidePopup
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import javax.swing.ListCellRenderer
import kotlin.reflect.KMutableProperty1

class WrappedDropDownList<D : ConnectionData, E : RenderableEntity>(private val prop: KMutableProperty1<D, String>,
                                                                    values: Array<E>,
                                                                    key: ModificationKey,
                                                                    initSettings: D,
                                                                    private val defaultValue: E? = null,
                                                                    render: ListCellRenderer<E>? = null) : WrappedNamedComponent<D>(key) {

  private val comboBox = ComboBoxWidePopup(values, defaultValue).apply {
    isSwingPopup = false
    renderer = render ?: CustomListCellRenderer<RenderableEntity> {
      if (it.title.isEmpty())
        it.id
      else
        "${it.title} [${it.id}]"
    }
  }

  init {
    val storedValue = prop.get(initSettings)
    comboBox.selectedItem = values.firstOrNull { it.id == storedValue } ?: defaultValue
  }

  fun setValue(value: E) {
    comboBox.selectedItem = value
  }

  override fun getValue(): E? = comboBox.item ?: if (comboBox.itemCount > 0) comboBox.getItemAt(0) as E else defaultValue

  override fun getComponent() = comboBox

  override fun apply(conn: D) {
    comboBox.item?.id?.let { prop.set(conn, it) }
  }

  override fun isModified(conn: D): Boolean = prop.get(conn) != getValue()?.id

  fun updateItems(newItems: Array<E>) {
    val selectedItem = comboBox.item
    comboBox.removeAllItems()
    newItems.forEach {
      comboBox.addItem(it)
    }
    comboBox.item = selectedItem ?: newItems.firstOrNull()
  }
}