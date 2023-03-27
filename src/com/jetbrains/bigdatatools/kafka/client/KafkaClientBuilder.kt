package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.common.settings.kerberos.BdtKerberosManager
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SaslConfigs.GSSAPI_MECHANISM
import org.apache.kafka.common.security.auth.SecurityProtocol
import java.util.*

object KafkaClientBuilder {
  fun createAdminClient(properties: Properties): BdtKafkaAdminClient {
    if (properties.isKerberosEnabled) {
      BdtKerberosManager.instance.validateKrb5()
      BdtKerberosManager.instance.setupKerberosValues()
    }
    return BdtKafkaAdminClient(AdminClient.create(properties))
  }

  private val Properties.isKerberosEnabled
    get() = this[SECURITY_PROTOCOL_CONFIG] in setOf(SecurityProtocol.SASL_PLAINTEXT.name, SecurityProtocol.SASL_SSL.name) &&
            this[SaslConfigs.SASL_MECHANISM] == GSSAPI_MECHANISM
}