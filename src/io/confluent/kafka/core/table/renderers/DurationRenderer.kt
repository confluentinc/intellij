package io.confluent.kafka.core.table.renderers

import io.confluent.kafka.core.util.TimeUtils
import javax.swing.SwingConstants

/** Renders seconds as readable interval description like "1d 5h" */
class DurationRenderer(private val neededAddChecking: Boolean = false) : MaterialTableCellRenderer() {

  init {
    horizontalAlignment = SwingConstants.RIGHT
  }

  override fun setValue(value: Any?) {
    if (value != null && value.toString().isNotBlank()) {
      val number = value.toString().toLong()
      if (neededAddChecking && number < 0) {
        super.setValue("-")
      } else {
        super.setValue(TimeUtils.intervalAsString(number))
      }
    } else {
      super.setValue(value)
    }
  }
}
