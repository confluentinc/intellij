package com.jetbrains.bigdatatools.kafka.aws.connection

import com.intellij.util.net.ssl.CertificateManager
import com.jetbrains.bigdatatools.kafka.aws.settings.AwsProxySettings
import com.jetbrains.bigdatatools.kafka.core.util.BdIdeRegistryUtil
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.http.apache.ProxyConfiguration
import software.amazon.awssdk.utils.AttributeMap
import java.io.File
import java.net.URL
import java.time.Duration
import javax.net.ssl.TrustManager

object AwsConnectionUtils {
  fun getDefaultCredentialFile(): File = File(getAwsDirectory(), "credentials")

  fun createHttpClient(proxySettings: AwsProxySettings?, trustAll: Boolean): ApacheHttpClient {
    val builder = ApacheHttpClient.builder()
    builder.connectionTimeout(Duration.ofMillis(BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT.toLong()))
    builder.socketTimeout(Duration.ofMillis(0))

    if (proxySettings != null) {
      builder.proxyConfiguration(buildProxy(proxySettings))
    }

    val attrMapBuilder = AttributeMap.builder()
    if (!trustAll) {
      builder.tlsTrustManagersProvider {
        arrayOf<TrustManager>(CertificateManager.getInstance().trustManager)
      }
      builder.tlsKeyManagersProvider {
        CertificateManager.getDefaultKeyManagers()
      }
    }
    else {
      attrMapBuilder.put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
    }

    return builder.buildWithDefaults(attrMapBuilder.build()) as ApacheHttpClient
  }

  fun createCredentialFileIfDoesNotExists(): File? {
    val file = getDefaultCredentialFile()
    if (file.exists())
      return null
    val text = getTemplateText() ?: return null
    file.parentFile?.mkdirs()
    file.createNewFile()
    file.writeText(text)
    return file
  }

  private fun buildProxy(proxy: AwsProxySettings): ProxyConfiguration? {
    val proxyBuilder = ProxyConfiguration.builder()
      .endpoint(URL("http", proxy.host, proxy.port, "").toURI())
      .preemptiveBasicAuthenticationEnabled(proxy.isPreemptiveBasicProxyAuth)
      .nonProxyHosts(proxy.nonProxyHosts?.split(",")?.toSet())
      .ntlmDomain(proxy.proxyDomain)
      .ntlmWorkstation(proxy.proxyWorkstation)
    //clientConfiguration.setDisableSocketProxy(proxy.isDisableSocketProxy)

    if (proxy.credentials != null) {
      proxyBuilder.username(proxy.credentials.userName)
      proxyBuilder.password(proxy.credentials.password?.toString())
    }

    return proxyBuilder.build()
  }


  private fun getTemplateText() = this.javaClass.getResourceAsStream("/template/aws-credentials-template")?.bufferedReader()?.readText()
  private fun getAwsDirectory() = File(getHomeDirectory(), ".aws")

  private fun getHomeDirectory() = System.getProperty("user.home") ?: throw SdkClientException.create(
    "Unable to load AWS profiles: " + "'user.home' System property is not set.")
}