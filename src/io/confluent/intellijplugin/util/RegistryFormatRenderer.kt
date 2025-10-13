package io.confluent.intellijplugin.util

import io.confluent.intellijplugin.core.table.renderers.MaterialTableCellRenderer
import io.confluent.intellijplugin.registry.KafkaRegistryFormat

class RegistryFormatRenderer : MaterialTableCellRenderer() {
    override fun setValue(value: Any?) {
        if (value == null) {
            super.setValue(KafkaMessagesBundle.message("monitoring.log.loading"))
        } else {
            super.setValue((value as? KafkaRegistryFormat)?.presentable ?: value.toString())
        }
    }
}