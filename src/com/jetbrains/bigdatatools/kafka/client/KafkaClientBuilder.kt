package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.common.connection.tunnel.BdtSshTunnelService
import com.jetbrains.bigdatatools.common.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.common.settings.kerberos.BdtKerberosManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import io.confluent.kafka.schemaregistry.client.rest.RestService
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
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

  fun createRegistryClient(project: Project?,
                           connectionData: KafkaConnectionData,
                           testConnection: Boolean): BdtKafkaRegistryClient? {
    val props = BdtPropertyComponent.parseProperties(connectionData.properties).associate {
      (it.name ?: "") to (it.value ?: "")
    } + BdtPropertyComponent.parseProperties(connectionData.registryProperties).associate {
      (it.name ?: "") to (it.value ?: "")
    }

    val url = props[SCHEMA_REGISTRY_URL_CONFIG]?.ifBlank { null } ?: connectionData.registryUrl?.ifBlank { null } ?: return null

    val tunnel = BdtSshTunnelService.createIfRequired(project, connectionData.getTunnelData(),
                                                      url, connectionData.innerId,
                                                      testConnection)
    val tunneledUrl = tunnel?.tunnelledUri ?: url

    val restService = RestService(tunneledUrl)

    restService.configure(props)
    val registryClient = BdtKafkaRegistryClient(restService)
    if (tunnel != null) {
      Disposer.register(registryClient, tunnel)
    }
    return registryClient
  }

  private val Properties.isKerberosEnabled
    get() = this[SECURITY_PROTOCOL_CONFIG] in setOf(SecurityProtocol.SASL_PLAINTEXT.name, SecurityProtocol.SASL_SSL.name) &&
            this[SaslConfigs.SASL_MECHANISM] == GSSAPI_MECHANISM
}