package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.common.settings.kerberos.BdtKerberosManager
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.security.auth.SecurityProtocol
import java.util.*

object KafkaClientBuilder {
  fun createAdminClient(properties: Properties): AdminClient {
    if (properties.isKerberosEnabled)
      BdtKerberosManager.instance.setupKerberosValues()
    return AdminClient.create(properties)
  }

  private val Properties.isKerberosEnabled
    get() = this[SECURITY_PROTOCOL_CONFIG] in setOf(SecurityProtocol.SASL_PLAINTEXT.name, SecurityProtocol.SASL_SSL.name)
}