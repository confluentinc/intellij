package com.jetbrains.bigdatatools.kafka.core.table.renderers

import java.text.SimpleDateFormat

class DateRenderer(private val neededAddChecking: Boolean = false) : MaterialTableCellRenderer() {

  companion object {
    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss") //DateFormat.getDateInstance(DateFormat.FULL)
  }

  override fun setValue(value: Any?) {
    try {
      if (value == null) {
        super.setValue("")
      } else if (neededAddChecking && value.toString().toLong() < 0) {
        super.setValue("-")
      } else {
        super.setValue(df.format(value))
      }
    } catch(e: Exception) {
      super.setValue(value)
    }
  }
}