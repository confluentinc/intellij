package io.confluent.kafka.util

import io.confluent.kafka.core.table.renderers.MaterialTableCellRenderer
import io.confluent.kafka.registry.KafkaRegistryFormat

class RegistryFormatRenderer : MaterialTableCellRenderer() {
  override fun setValue(value: Any?) {
    if (value == null) {
      super.setValue(KafkaMessagesBundle.message("monitoring.log.loading"))
    }
    else {
      super.setValue((value as? KafkaRegistryFormat)?.presentable ?: value.toString())
    }
  }
}