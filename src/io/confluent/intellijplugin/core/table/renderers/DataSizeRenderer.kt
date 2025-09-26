package io.confluent.intellijplugin.core.table.renderers

import io.confluent.intellijplugin.core.util.SizeUtils
import javax.swing.SwingConstants

/** 1024 -> "1 kB" */
class DataSizeRenderer : MaterialTableCellRenderer() {
  init {
    horizontalAlignment = SwingConstants.RIGHT
  }

  override fun setValue(value: Any?) {
    text = if (value == null) "" else SizeUtils.toString(value as Long)
  }
}