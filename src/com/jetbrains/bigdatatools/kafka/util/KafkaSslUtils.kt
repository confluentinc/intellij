package com.jetbrains.bigdatatools.kafka.util

import com.intellij.execution.rmi.ssl.SslUtil
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.bigdatatools.common.connection.exception.BdtConfigurationException
import org.apache.kafka.common.config.SslConfigs
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.util.*

object KafkaSslUtils {
  val SSL_KEY_LOCATION = "ssl.key.location"
  val SSL_CERTIFICATE_LOCATION = "ssl.certificate.location"
  val SSL_CA_LOCATION = "ssl.ca.location"

  fun addMigratedToProperties(properties: Properties) {
    if (properties[SSL_CA_LOCATION] != null) {
      val caLocation = properties[SSL_CA_LOCATION]?.toString() ?: return
      val transformedPath = transformCaToTruststore(caLocation)
      properties[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = transformedPath.canonicalPath
    }
    if (properties[SSL_KEY_LOCATION] != null || properties[SSL_CERTIFICATE_LOCATION] != null) {
      val serviceCertPath = properties[SSL_CERTIFICATE_LOCATION]?.toString() ?: throw BdtConfigurationException(
        KafkaMessagesBundle.message("error.ssl.key.and.ssl.certificate.should.be.defined.together"))
      val serviceKeyPath = properties[SSL_KEY_LOCATION]?.toString() ?: throw BdtConfigurationException(
        KafkaMessagesBundle.message("error.ssl.key.and.ssl.certificate.should.be.defined.together"))
      val transformedPath = transformKeyToKeystore(serviceCertPath, serviceKeyPath)
      properties[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = transformedPath.canonicalPath
      properties[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = ""
    }

  }

  private fun transformKeyToKeystore(serviceCertPath: String, serviceKeyPath: String): File {
    try {
      if (!FileUtil.exists(serviceCertPath))
        throw FileNotFoundException(serviceCertPath)
      if (!FileUtil.exists(serviceKeyPath))
        throw FileNotFoundException(serviceKeyPath)

      val caCertificates = SslUtil.loadCertificates(serviceCertPath)

      val key = SslUtil.readPrivateKey(serviceKeyPath)
      val instance = KeyStore.getInstance("PKCS12")
      instance.load(null, null)
      instance.setKeyEntry("service_key", key, charArrayOf(), caCertificates.toTypedArray())
      val createTempFile = FileUtil.createTempFile("", "client.keystore.p12", false)
      instance.store(createTempFile.outputStream(), charArrayOf())
      return createTempFile
    }
    catch (t: Throwable) {
      throw t
    }

  }


  private fun transformCaToTruststore(carFilePath: String): File {
    try {
      val caCertificates = SslUtil.loadCertificates(carFilePath)

      val instance = KeyStore.getInstance("JKS")
      instance.load(null, null)
      caCertificates.withIndex().forEach {
        instance.setCertificateEntry("CA-${it.index}", it.value)
      }
      val createTempFile = FileUtil.createTempFile("", "client.truststore.jks", false)
      instance.store(createTempFile.outputStream(), "".toCharArray())
      return createTempFile
    }
    catch (t: Throwable) {
      throw t
    }
  }
}