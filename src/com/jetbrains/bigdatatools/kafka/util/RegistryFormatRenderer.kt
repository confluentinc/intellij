package com.jetbrains.bigdatatools.kafka.util

import com.jetbrains.bigdatatools.common.table.renderers.MaterialTableCellRenderer
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat

class RegistryFormatRenderer : MaterialTableCellRenderer() {
  override fun setValue(value: Any?) {
    if (value == null) {
      super.setValue(MessagesBundle.message("monitoring.log.loading"))
    }
    else {
      super.setValue((value as? KafkaRegistryFormat)?.presentable ?: value.toString())
    }
  }
}