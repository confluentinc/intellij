package io.confluent.intellijplugin.core.table.renderers

import javax.swing.SwingConstants

open class NumberRenderer : MaterialTableCellRenderer() {
  init {
    horizontalAlignment = SwingConstants.RIGHT
  }
}