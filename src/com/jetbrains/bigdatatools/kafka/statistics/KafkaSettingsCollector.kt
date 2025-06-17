package com.jetbrains.bigdatatools.kafka.statistics

import com.jetbrains.bigdatatools.common.constants.BdtConnectionType
import com.jetbrains.bigdatatools.common.rfs.statistics.v2.BdtSettingsCollector
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.AuthenticationType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.rfs.*
import com.jetbrains.bigdatatools.kafka.settings.KafkaSettingsCustomizer

class KafkaSettingsCollector : BdtSettingsCollector() {
  override val connectionType: BdtConnectionType = BdtConnectionType.KAFKA

  init {
    registryEvent(KafkaSettingsCustomizer::nameField)
    registryEvent(KafkaSettingsCustomizer::url)
    registryStringEnumEvent(KafkaSettingsCustomizer::registryType, KafkaRegistryType.entries.map { it.id })
    registryStringEnumEvent(KafkaSettingsCustomizer::brokerConfSource, KafkaConfigurationSource.entries.map { it.id })
    registryStringEnumEvent(KafkaSettingsCustomizer::brokerCloudSource, KafkaCloudType.entries.map { it.id })
    registryStringEnumEvent(KafkaSettingsCustomizer::brokerPropertiesSource, KafkaPropertySource.entries.map { it.id })
    registryEvent(KafkaSettingsCustomizer::brokerPropertiesEditor)
    registryEvent(KafkaSettingsCustomizer::brokerPropertiesFile)
    registryEvent(KafkaSettingsCustomizer::brokerConfluentConf)
    registryEvent(KafkaSettingsCustomizer::brokerMskUrl)
    registryStringEnumEvent(KafkaSettingsCustomizer::brokerAuthType, KafkaAuthMethod.entries.map { it.id })

    registryEvent(KafkaSettingsCustomizer::brokerMskCloudAccessKey)
    registryEvent(KafkaSettingsCustomizer::brokerMskCloudSecretKey)
    registryStringEnumEvent(KafkaSettingsCustomizer::brokerMskCloudAuthType, AuthenticationType.values.map { it.id })
    registryEvent(KafkaSettingsCustomizer::brokerMskCloudProfile)

    registryEvent(KafkaSettingsCustomizer::brokerAwsIamAccess)
    registryEvent(KafkaSettingsCustomizer::brokerAwsIamSecretKey)
    registryStringEnumEvent(KafkaSettingsCustomizer::brokerAwsIamAuthType, AuthenticationType.values.map { it.id })
    registryEvent(KafkaSettingsCustomizer::brokerAwsIamProfile)

    registryEvent(KafkaSettingsCustomizer::brokerSaslKeytab)
    registryStringEnumEvent(KafkaSettingsCustomizer::brokerSaslMechanism, KafkaSaslMechanism.entries.map { it.name })
    registryEvent(KafkaSettingsCustomizer::brokerSaslPassword)
    registryEvent(KafkaSettingsCustomizer::brokerSaslPrincipal)
    registryEvent(KafkaSettingsCustomizer::brokerSaslUsername)
    registryCheckboxEvent(KafkaSettingsCustomizer::brokerSaslUseTicketCache)
    registryCheckboxEvent(KafkaSettingsCustomizer::brokerSaslSecurityProtocol)

    registryEvent(KafkaSettingsCustomizer::brokerSslKeyPassword)
    registryEvent(KafkaSettingsCustomizer::brokerSslKeystorePassword)
    registryEvent(KafkaSettingsCustomizer::brokerSslTruststorePassword)
    registryEvent(KafkaSettingsCustomizer::brokerSslKeystoreLocation)
    registryEvent(KafkaSettingsCustomizer::brokerSslTrustoreLocation)
    registryCheckboxEvent(KafkaSettingsCustomizer::brokerSslUseKeystore)
    registryCheckboxEvent(KafkaSettingsCustomizer::brokerSslEnableValidation)

    registryEvent(KafkaSettingsCustomizer::registryConfluentUrl)
    registryStringEnumEvent(KafkaSettingsCustomizer::registryConfluentSource, KafkaConfigurationSource.entries.map { it.id })
    registryEvent(KafkaSettingsCustomizer::registryConfluentProperties)
    registryStringEnumEvent(KafkaSettingsCustomizer::registryConfluentAuth, SchemaRegistryAuthType.entries.map { it.name })
    registryEvent(KafkaSettingsCustomizer::registryConfluentBasicAuth)
    registryEvent(KafkaSettingsCustomizer::registryConfluentBasicPassword)
    registryEvent(KafkaSettingsCustomizer::registryConfluentBearerToken)
    registryCheckboxEvent(KafkaSettingsCustomizer::registryConfluentUseProxy)
    registryEvent(KafkaSettingsCustomizer::registryConfluentProxyUrl)
    registryCheckboxEvent(KafkaSettingsCustomizer::registryConfluentUseBrokerSsl)
    registryEvent(KafkaSettingsCustomizer::registryConfluentSslKeyPassword)
    registryEvent(KafkaSettingsCustomizer::registryConfluentSslKeystorePassword)
    registryEvent(KafkaSettingsCustomizer::registryConfluentSslTruststorePassword)
    registryEvent(KafkaSettingsCustomizer::registryConfluentSslKeystoreLocation)
    registryEvent(KafkaSettingsCustomizer::registryConfluentSslKeystoreLocation)
    registryEvent(KafkaSettingsCustomizer::registryConfluentSslTrustoreLocation)
    registryCheckboxEvent(KafkaSettingsCustomizer::registryConfluentSslUseKeystore)
    registryCheckboxEvent(KafkaSettingsCustomizer::registryConfluentSslEnableValidation)

    registryEvent(KafkaSettingsCustomizer::registryGlueAccessKey)
    registryEvent(KafkaSettingsCustomizer::registryGlueSecretKey)
    registryStringEnumEvent(KafkaSettingsCustomizer::registryGlueAuthType, AuthenticationType.values.map { it.id })
    registryEvent(KafkaSettingsCustomizer::registryGlueProfile)
    registryEvent(KafkaSettingsCustomizer::registryGlueRegion)
    registryEvent(KafkaSettingsCustomizer::registryGlueRegistryName)

    registryEvent(KafkaSettingsCustomizer::tunnelField)
    registryCheckboxEvent(KafkaSettingsCustomizer::enableTunnelField)

    init()
  }

  object Util {
    fun getInstance() = getInstance(BdtConnectionType.KAFKA) as? KafkaSettingsCollector ?: KafkaSettingsCollector()
  }
}
