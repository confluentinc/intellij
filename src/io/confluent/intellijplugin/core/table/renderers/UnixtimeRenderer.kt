package io.confluent.intellijplugin.core.table.renderers

import java.text.SimpleDateFormat
import java.util.*

/**
 * 1677586754 -> 2023-02-28 12:19:14
 */
class UnixtimeRenderer : MaterialTableCellRenderer() {

  companion object {
    private var df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  }

  override fun setValue(value: Any?) {
    value ?: return
    val longDate = value.toString().toLongOrNull() ?: return
    super.setValue(df.format(Date(longDate)))
  }
}