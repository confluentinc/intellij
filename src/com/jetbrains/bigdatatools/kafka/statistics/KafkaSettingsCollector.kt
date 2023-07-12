package com.jetbrains.bigdatatools.kafka.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.jetbrains.bigdatatools.common.constants.BdtConnectionType
import com.jetbrains.bigdatatools.common.rfs.statistics.v2.BdtSettingsCollector
import com.jetbrains.bigdatatools.kafka.settings.KafkaSettingsCustomizer

class KafkaSettingsCollector : BdtSettingsCollector(BdtConnectionType.KAFKA) {
  private val GROUP = EventLogGroup(GROUP_ID, 1)

  override val groupDescription: String = "Kafka"

  init {
    registryField(KafkaSettingsCustomizer::nameField)
    registryField(KafkaSettingsCustomizer::url)
    registryField(KafkaSettingsCustomizer::registryType)
    registryField(KafkaSettingsCustomizer::brokerConfSource)
    registryField(KafkaSettingsCustomizer::brokerCloudSource)
    registryField(KafkaSettingsCustomizer::brokerPropertiesSource)
    registryField(KafkaSettingsCustomizer::brokerPropertiesEditor)
    registryField(KafkaSettingsCustomizer::brokerPropertiesFile)
    registryField(KafkaSettingsCustomizer::brokerConfluentConf)
    registryField(KafkaSettingsCustomizer::brokerMskUrl)
    registryField(KafkaSettingsCustomizer::brokerAuthType)

    registryField(KafkaSettingsCustomizer::brokerMskCloudAccessKey)
    registryField(KafkaSettingsCustomizer::brokerMskCloudSecretKey)
    registryField(KafkaSettingsCustomizer::brokerMskCloudAuthType)
    registryField(KafkaSettingsCustomizer::brokerMskCloudProfile)

    registryField(KafkaSettingsCustomizer::brokerAwsIamAccess)
    registryField(KafkaSettingsCustomizer::brokerAwsIamSecretKey)
    registryField(KafkaSettingsCustomizer::brokerAwsIamAuthType)
    registryField(KafkaSettingsCustomizer::brokerAwsIamProfile)

    registryField(KafkaSettingsCustomizer::brokerSaslKeytab)
    registryField(KafkaSettingsCustomizer::brokerSaslMechanism)
    registryField(KafkaSettingsCustomizer::brokerSaslPassword)
    registryField(KafkaSettingsCustomizer::brokerSaslPrincipal)
    registryField(KafkaSettingsCustomizer::brokerSaslUsername)
    registryField(KafkaSettingsCustomizer::brokerSaslUseTicketCache)
    registryField(KafkaSettingsCustomizer::brokerSaslSecurityProtocol)

    registryField(KafkaSettingsCustomizer::brokerSslKeyPassword)
    registryField(KafkaSettingsCustomizer::brokerSslKeystorePassword)
    registryField(KafkaSettingsCustomizer::brokerSslTruststorePassword)
    registryField(KafkaSettingsCustomizer::brokerSslKeystoreLocation)
    registryField(KafkaSettingsCustomizer::brokerSslTrustoreLocation)
    registryField(KafkaSettingsCustomizer::brokerSslUseKeystore)
    registryField(KafkaSettingsCustomizer::brokerSslEnableValidation)

    registryField(KafkaSettingsCustomizer::registryConfluentUrl)
    registryField(KafkaSettingsCustomizer::registryConfluentSource)
    registryField(KafkaSettingsCustomizer::registryConfluentProperties)
    registryField(KafkaSettingsCustomizer::registryConfluentAuth)
    registryField(KafkaSettingsCustomizer::registryConfluentBasicAuth)
    registryField(KafkaSettingsCustomizer::registryConfluentBasicPassword)
    registryField(KafkaSettingsCustomizer::registryConfluentBearerToken)
    registryField(KafkaSettingsCustomizer::registryConfluentUseProxy)
    registryField(KafkaSettingsCustomizer::registryConfluentProxyUrl)
    registryField(KafkaSettingsCustomizer::registryConfluentUseBrokerSsl)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeyPassword)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeystorePassword)
    registryField(KafkaSettingsCustomizer::registryConfluentSslTruststorePassword)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeystoreLocation)
    registryField(KafkaSettingsCustomizer::registryConfluentSslKeystoreLocation)
    registryField(KafkaSettingsCustomizer::registryConfluentSslTrustoreLocation)
    registryField(KafkaSettingsCustomizer::registryConfluentSslUseKeystore)
    registryField(KafkaSettingsCustomizer::registryConfluentSslEnableValidation)

    registryField(KafkaSettingsCustomizer::registryGlueAccessKey)
    registryField(KafkaSettingsCustomizer::registryGlueSecretKey)
    registryField(KafkaSettingsCustomizer::registryGlueAuthType)
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