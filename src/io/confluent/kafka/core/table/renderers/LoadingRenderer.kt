package io.confluent.kafka.core.table.renderers

import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.SwingConstants

class LoadingRenderer(rightAligned: Boolean) : MaterialTableCellRenderer() {

  init {
    if (rightAligned) {
      horizontalAlignment = SwingConstants.RIGHT
    }
  }

  override fun setValue(value: Any?) {
    when {
      value == null -> super.setValue(KafkaMessagesBundle.message("monitoring.log.loading"))
      value is List<*> -> super.setValue(value.joinToString(separator = ", ") { it.toString() })
      value.toString().toIntOrNull() == -1 -> super.setValue("")
      else -> super.setValue(value.toString())
    }
  }
}