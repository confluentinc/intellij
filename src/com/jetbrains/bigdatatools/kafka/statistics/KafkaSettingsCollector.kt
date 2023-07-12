package com.jetbrains.bigdatatools.kafka.statistics

import com.intellij.bigdatatools.aws.connection.auth.AuthenticationType
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.jetbrains.bigdatatools.common.constants.BdtConnectionType
import com.jetbrains.bigdatatools.common.rfs.statistics.v2.BdtSettingsCollector
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.rfs.*
import com.jetbrains.bigdatatools.kafka.settings.KafkaSettingsCustomizer

class KafkaSettingsCollector : BdtSettingsCollector(BdtConnectionType.KAFKA) {
  private val GROUP = EventLogGroup(GROUP_ID, 1)

  override val groupDescription: String = "Kafka"

  init {
    registryField(KafkaSettingsCustomizer::nameField)
    registryField(KafkaSettingsCustomizer::url)
    registryStringEnumField(KafkaSettingsCustomizer::registryType, KafkaRegistryType.values().map { it.id })
    registryStringEnumField(KafkaSettingsCustomizer::brokerConfSource, KafkaConfigurationSource.values().map { it.id })
    registryStringEnumField(KafkaSettingsCustomizer::brokerCloudSource, KafkaCloudType.values().map { it.id })
    registryStringEnumField(KafkaSettingsCustomizer::brokerPropertiesSource, KafkaPropertySource.values().map { it.id })
    registryField(KafkaSettingsCustomizer::brokerPropertiesEditor)
    registryField(KafkaSettingsCustomizer::brokerPropertiesFile)
    registryField(KafkaSettingsCustomizer::brokerConfluentConf)
    registryField(KafkaSettingsCustomizer::brokerMskUrl)
    registryStringEnumField(KafkaSettingsCustomizer::brokerAuthType, KafkaAuthMethod.values().map { it.id })

    registryField(KafkaSettingsCustomizer::brokerMskCloudAccessKey)
    registryField(KafkaSettingsCustomizer::brokerMskCloudSecretKey)
    registryStringEnumField(KafkaSettingsCustomizer::brokerMskCloudAuthType, AuthenticationType.values.map { it.id })
    registryField(KafkaSettingsCustomizer::brokerMskCloudProfile)

    registryField(KafkaSettingsCustomizer::brokerAwsIamAccess)
    registryField(KafkaSettingsCustomizer::brokerAwsIamSecretKey)
    registryStringEnumField(KafkaSettingsCustomizer::brokerAwsIamAuthType, AuthenticationType.values.map { it.id })
    registryField(KafkaSettingsCustomizer::brokerAwsIamProfile)

    registryField(KafkaSettingsCustomizer::brokerSaslKeytab)
    registryStringEnumField(KafkaSettingsCustomizer::brokerSaslMechanism, KafkaSaslMechanism.values().map { it.name })
    registryField(KafkaSettingsCustomizer::brokerSaslPassword)
    registryField(KafkaSettingsCustomizer::brokerSaslPrincipal)
    registryField(KafkaSettingsCustomizer::brokerSaslUsername)
    registryCheckboxField(KafkaSettingsCustomizer::brokerSaslUseTicketCache)
    registryCheckboxField(KafkaSettingsCustomizer::brokerSaslSecurityProtocol)

    registryField(KafkaSettingsCustomizer::brokerSslKeyPassword)
    registryField(KafkaSettingsCustomizer::brokerSslKeystorePassword)
    registryField(KafkaSettingsCustomizer::brokerSslTruststorePassword)
    registryField(KafkaSettingsCustomizer::brokerSslKeystoreLocation)
    registryField(KafkaSettingsCustomizer::brokerSslTrustoreLocation)
    registryCheckboxField(KafkaSettingsCustomizer::brokerSslUseKeystore)
    registryCheckboxField(KafkaSettingsCustomizer::brokerSslEnableValidation)

    registryField(KafkaSettingsCustomizer::registryConfluentUrl)
    registryStringEnumField(KafkaSettingsCustomizer::registryConfluentSource, KafkaConfigurationSource.values().map { it.id })
    registryField(KafkaSettingsCustomizer::registryConfluentProperties)
    registryStringEnumField(KafkaSettingsCustomizer::registryConfluentAuth, SchemaRegistryAuthType.values().map { it.name })
    registryField(KafkaSettingsCustomizer::registryConfluentBasicAuth)
    registryField(KafkaSettingsCustomizer::registryConfluentBasicPassword)
    registryField(KafkaSettingsCustomizer::registryConfluentBearerToken)
    registryCheckboxField(KafkaSettingsCustomizer::registryConfluentUseProxy)
    registryField(KafkaSettingsCustomizer::registryConfluentProxyUrl)
    registryCheckboxField(KafkaSettingsCustomizer::registryConfluentUseBrokerSsl)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeyPassword)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeystorePassword)
    registryField(KafkaSettingsCustomizer::registryConfluentSslTruststorePassword)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeystoreLocation)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeystoreLocation)
    registryField(KafkaSettingsCustomizer::registryConfluentSslTrustoreLocation)
    registryCheckboxField(KafkaSettingsCustomizer::registryConfluentSslUseKeystore)
    registryCheckboxField(KafkaSettingsCustomizer::registryConfluentSslEnableValidation)

    registryField(KafkaSettingsCustomizer::registryGlueAccessKey)
    registryField(KafkaSettingsCustomizer::registryGlueSecretKey)
    registryStringEnumField(KafkaSettingsCustomizer::registryGlueAuthType, AuthenticationType.values.map { it.id })
    registryField(KafkaSettingsCustomizer::registryGlueProfile)
    registryField(KafkaSettingsCustomizer::registryGlueRegion)
    registryField(KafkaSettingsCustomizer::registryGlueRegistryName)

    init()
  }

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    const val GROUP_ID: String = "bigdatatools.settings.kafka"

    fun getInstance() = getInstance(GROUP_ID) as? KafkaSettingsCollector ?: KafkaSettingsCollector()
  }
}