package com.jetbrains.bigdatatools.kafka.util

import com.jetbrains.bigdatatools.kafka.core.table.renderers.MaterialTableCellRenderer
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat

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