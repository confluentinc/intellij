@file:Suppress("unused", "DEPRECATION", "RemoveRedundantQualifierName")

package io.confluent.kafka.rfs

typealias Prop = kotlin.reflect.KMutableProperty1<out io.confluent.kafka.core.settings.connections.ConnectionData, *>

object SerializableProperties {
  // serializable property
  val simpleProperties = listOf<Prop>(
    io.confluent.kafka.core.settings.connections.ConnectionData::anonymous,
    io.confluent.kafka.rfs.KafkaConnectionData::brokerCloudSource,
    io.confluent.kafka.rfs.KafkaConnectionData::brokerConfigurationSource,
    io.confluent.kafka.rfs.KafkaConnectionData::glueRegistryName,
    io.confluent.kafka.rfs.KafkaConnectionData::glueSettings,

    io.confluent.kafka.core.settings.connections.ConnectionData::groupId,
    io.confluent.kafka.core.settings.connections.ConnectionData::innerId,

    io.confluent.kafka.core.settings.connections.ConnectionData::isEnabled,
    io.confluent.kafka.core.settings.connections.ConnectionData::isPerProject,

    io.confluent.kafka.core.settings.connections.ConnectionData::name,
    io.confluent.kafka.core.settings.connections.ConnectionData::port,

    io.confluent.kafka.rfs.KafkaConnectionData::properties,
    io.confluent.kafka.rfs.KafkaConnectionData::propertyFilePath,
    io.confluent.kafka.rfs.KafkaConnectionData::propertySource,

    io.confluent.kafka.rfs.KafkaConnectionData::registryConfSource,
    io.confluent.kafka.rfs.KafkaConnectionData::registryProperties,
    io.confluent.kafka.rfs.KafkaConnectionData::registryType,
    io.confluent.kafka.rfs.KafkaConnectionData::registryUrl,
    io.confluent.kafka.rfs.KafkaConnectionData::registryUseBrokerSsl,

    io.confluent.kafka.core.rfs.settings.local.RfsLocalConnectionData::rootPath,
    io.confluent.kafka.core.settings.connections.ConnectionData::sourceConnection,
    io.confluent.kafka.core.settings.connections.ConnectionData::uri,
    io.confluent.kafka.rfs.KafkaConnectionData::version,
  )

  // serializable property, override-implement
  val implementingProperties = listOf<Prop>(
    io.confluent.kafka.rfs.KafkaConnectionData::tunnel,
  )

  val allProperties = simpleProperties + implementingProperties

  val usedClasses = listOf(
    String::class,
    Int::class,
    Boolean::class,
    io.confluent.kafka.rfs.KafkaCloudType::class,
    io.confluent.kafka.rfs.KafkaConfigurationSource::class,
    Map::class,
    Set::class,
    Collection::class,
    io.confluent.kafka.rfs.KafkaPropertySource::class,
    io.confluent.kafka.core.connection.ProxyEnableType::class,
    io.confluent.kafka.core.connection.ProxyType::class,
    io.confluent.kafka.registry.KafkaRegistryType::class,
    List::class,
    io.confluent.kafka.core.connection.tunnel.model.ConnectionSshTunnelDataLegacy::class,
  )
}