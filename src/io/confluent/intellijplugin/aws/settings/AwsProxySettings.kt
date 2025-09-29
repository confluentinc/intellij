package io.confluent.intellijplugin.aws.settings

import com.intellij.credentialStore.Credentials

data class AwsProxySettings(val host: String,
                            val port: Int,
                            val credentials: Credentials? = null,
                            val proxyWorkstation: String = "",
                            val nonProxyHosts: String? = null,
                            val isDisableSocketProxy: Boolean = false,
                            val proxyDomain: String? = null,
                            val isPreemptiveBasicProxyAuth: Boolean = false)