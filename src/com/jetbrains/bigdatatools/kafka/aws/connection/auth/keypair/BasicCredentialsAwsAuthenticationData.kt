package com.jetbrains.bigdatatools.kafka.aws.connection.auth.keypair

import com.jetbrains.bigdatatools.kafka.aws.connection.auth.AuthenticationType
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.common.AwsAuthenticationData
import com.jetbrains.bigdatatools.kafka.aws.ui.external.AwsSettingsInfo

class BasicCredentialsAwsAuthenticationData(private val awsSettingsInfo: AwsSettingsInfo) : AwsAuthenticationData() {
  override val authType: String = AuthenticationType.KEY_PAIR.id

  override fun getCredentialsProvider() = FromCredAttributesBdtAwsCredentialsProvider(awsSettingsInfo)
}