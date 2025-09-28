package io.confluent.intellijplugin.core.ui.components

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.GroupedComboBoxRenderer
import javax.swing.JList

open class BdtGroupRender<T>(comboBox: ComboBox<T>?,
                             val groups: kotlin.collections.List<Pair<String, kotlin.collections.List<T>>>) : GroupedComboBoxRenderer<T>(
  comboBox) {
  override fun isSeparatorVisible(list: JList<out T>?, value: T) = separatorFor(value) != null
  override fun getCaption(list: JList<out T>?, value: T) = separatorFor(value)?.text

  override fun separatorFor(value: T): ListSeparator? {
    val groupTitle = groups.firstNotNullOfOrNull { (title, group) ->
      val first = group.firstOrNull()
      if (first == value)
        title
      else
        null
    } ?: return null
    return ListSeparator(groupTitle)
  }
}