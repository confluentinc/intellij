package com.jetbrains.bigdatatools.kafka.core.table.renderers

import javax.swing.SwingConstants

open class NumberRenderer : MaterialTableCellRenderer() {
  init {
    horizontalAlignment = SwingConstants.RIGHT
  }
}