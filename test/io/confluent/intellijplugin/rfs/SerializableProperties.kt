@file:Suppress("unused", "DEPRECATION", "RemoveRedundantQualifierName")

package io.confluent.intellijplugin.rfs

import io.confluent.intellijplugin.core.connection.ProxyEnableType
import io.confluent.intellijplugin.core.connection.ProxyType

typealias Prop = kotlin.reflect.KMutableProperty1<out io.confluent.intellijplugin.core.settings.connections.ConnectionData, *>

object SerializableProperties {
    // serializable property
    val simpleProperties = listOf<Prop>(
        io.confluent.intellijplugin.core.settings.connections.ConnectionData::anonymous,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::brokerCloudSource,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::brokerConfigurationSource,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::glueRegistryName,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::glueSettings,

        io.confluent.intellijplugin.core.settings.connections.ConnectionData::groupId,
        io.confluent.intellijplugin.core.settings.connections.ConnectionData::innerId,

        io.confluent.intellijplugin.core.settings.connections.ConnectionData::isEnabled,
        io.confluent.intellijplugin.core.settings.connections.ConnectionData::isPerProject,

        io.confluent.intellijplugin.core.settings.connections.ConnectionData::name,
        io.confluent.intellijplugin.core.settings.connections.ConnectionData::port,

        io.confluent.intellijplugin.rfs.KafkaConnectionData::properties,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::propertyFilePath,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::propertySource,

        io.confluent.intellijplugin.rfs.KafkaConnectionData::registryConfSource,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::registryProperties,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::registryType,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::registryUrl,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::registryUseBrokerSsl,

        io.confluent.intellijplugin.core.rfs.settings.local.RfsLocalConnectionData::rootPath,
        io.confluent.intellijplugin.core.settings.connections.ConnectionData::sourceConnection,
        io.confluent.intellijplugin.core.settings.connections.ConnectionData::uri,
        io.confluent.intellijplugin.rfs.KafkaConnectionData::version,
    )

    // serializable property, override-implement
    val implementingProperties = listOf<Prop>(
        io.confluent.intellijplugin.rfs.KafkaConnectionData::tunnel,
    )

    val allProperties = simpleProperties + implementingProperties

    val usedClasses = listOf(
        String::class,
        Int::class,
        Boolean::class,
        io.confluent.intellijplugin.rfs.KafkaCloudType::class,
        io.confluent.intellijplugin.rfs.KafkaConfigurationSource::class,
        Map::class,
        Set::class,
        Collection::class,
        io.confluent.intellijplugin.rfs.KafkaPropertySource::class,
        ProxyEnableType::class,
        ProxyType::class,
        io.confluent.intellijplugin.registry.KafkaRegistryType::class,
        List::class,
        io.confluent.intellijplugin.core.connection.tunnel.model.ConnectionSshTunnelDataLegacy::class,
    )
}