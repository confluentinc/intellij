package io.confluent.kafka.core.table.renderers

import java.text.DecimalFormat

/**
 * Table cell renderer for displaying double values without trailing zeros.
 * "52.0" -> "52"
 */
class DoubleRenderer : NumberRenderer() {
  companion object {
    // We use this format to avoid 1e-153 which are confusing for most users.
    var format = DecimalFormat("#.###############")
  }

  override fun setValue(value: Any?) {
    text = format.format(value)
  }
}
