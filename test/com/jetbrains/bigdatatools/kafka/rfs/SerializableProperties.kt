@file:Suppress("unused", "DEPRECATION", "RemoveRedundantQualifierName")

package com.jetbrains.bigdatatools.kafka.rfs

typealias Prop = kotlin.reflect.KMutableProperty1<out com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData, *>

object SerializableProperties {
  // serializable property
  val simpleProperties = listOf<Prop>(
    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::anonymous,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::brokerCloudSource,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::brokerConfigurationSource,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::glueRegistryName,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::glueSettings,

    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::groupId,
    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::innerId,

    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::isEnabled,
    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::isPerProject,

    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::name,
    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::port,

    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::properties,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::propertyFilePath,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::propertySource,

    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::registryConfSource,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::registryProperties,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::registryType,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::registryUrl,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::registryUseBrokerSsl,

    com.jetbrains.bigdatatools.kafka.core.rfs.settings.local.RfsLocalConnectionData::rootPath,
    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::sourceConnection,
    com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData::uri,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::version,
  )

  // serializable property, override-implement
  val implementingProperties = listOf<Prop>(
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData::tunnel,
  )

  val allProperties = simpleProperties + implementingProperties

  val usedClasses = listOf(
    String::class,
    Int::class,
    Boolean::class,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaCloudType::class,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaConfigurationSource::class,
    Map::class,
    Set::class,
    Collection::class,
    com.jetbrains.bigdatatools.kafka.rfs.KafkaPropertySource::class,
    com.jetbrains.bigdatatools.kafka.core.connection.ProxyEnableType::class,
    com.jetbrains.bigdatatools.kafka.core.connection.ProxyType::class,
    com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType::class,
    List::class,
    com.jetbrains.bigdatatools.kafka.core.connection.tunnel.model.ConnectionSshTunnelDataLegacy::class,
  )
}