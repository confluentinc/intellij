package com.jetbrains.bigdatatools.kafka.util

import com.jetbrains.bigdatatools.core.ui.components.ConnectionProperty
import com.jetbrains.bigdatatools.core.util.withPluginClassLoader
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.config.ConfigDef.NO_DEFAULT_VALUE
import java.util.*

object KafkaPropertiesUtils {
  fun getAdminPropertiesDescriptions() =
    AdminClientConfig.configDef().configKeys().values.mapNotNull { configKey ->
      ConnectionProperty(
        propertyName = configKey.name,
        default = if (configKey.defaultValue == NO_DEFAULT_VALUE) "" else configKey.defaultValue?.toString() ?: "null",
        meaning = configKey.documentation,
        rightSideInfo = configKey.type.name.lowercase().replaceFirstChar {
          if (it.isLowerCase()) it.titlecase(Locale.getDefault())
          else it.toString()
        }
      )
    }

  fun getRegistryPropertiesDescriptions() = withPluginClassLoader {
    AbstractKafkaSchemaSerDeConfig.baseConfigDef().configKeys().values.mapNotNull { configKey ->
      ConnectionProperty(
        propertyName = configKey.name,
        default = if (configKey.defaultValue == NO_DEFAULT_VALUE) "" else configKey.defaultValue?.toString() ?: "null",
        meaning = configKey.documentation,
        rightSideInfo = configKey.type.name.lowercase().replaceFirstChar {
          if (it.isLowerCase()) it.titlecase(Locale.getDefault())
          else it.toString()
        }
      )
    }
  }
}