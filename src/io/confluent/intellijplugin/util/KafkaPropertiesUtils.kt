package io.confluent.intellijplugin.util

import io.confluent.intellijplugin.core.ui.components.ConnectionProperty
import io.confluent.intellijplugin.core.util.withPluginClassLoader
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.ConfigDef.ConfigKey
import org.apache.kafka.common.config.ConfigDef.NO_DEFAULT_VALUE
import java.util.*

/**
 * Kafka's config classes resolve class-typed defaults (e.g. `sasl.oauthbearer.jwt.retriever.class`) through the
 * thread's context classloader during static initialization. These are called from the EDT, whose context
 * classloader cannot see the plugin's Kafka jars, so every access goes through [withPluginClassLoader]. A failed
 * static initialization would break the config class for the rest of the IDE session.
 */
object KafkaPropertiesUtils {
    fun getConsumerConfigKeys(): Map<String, ConfigKey> = withPluginClassLoader {
        ConsumerConfig.configDef().configKeys()
    }

    fun getAdminPropertiesDescriptions() = withPluginClassLoader {
        AdminClientConfig.configDef().configKeys().values.mapNotNull { configKey ->
            ConnectionProperty(
                propertyName = configKey.name,
                default = if (configKey.defaultValue == NO_DEFAULT_VALUE) "" else configKey.defaultValue?.toString()
                    ?: "null",
                meaning = configKey.documentation,
                rightSideInfo = configKey.type.name.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                    else it.toString()
                }
            )
        }
    }

    fun getRegistryPropertiesDescriptions() = withPluginClassLoader {
        AbstractKafkaSchemaSerDeConfig.baseConfigDef().configKeys().values.mapNotNull { configKey ->
            ConnectionProperty(
                propertyName = configKey.name,
                default = if (configKey.defaultValue == NO_DEFAULT_VALUE) "" else configKey.defaultValue?.toString()
                    ?: "null",
                meaning = configKey.documentation,
                rightSideInfo = configKey.type.name.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                    else it.toString()
                }
            )
        }
    }
}