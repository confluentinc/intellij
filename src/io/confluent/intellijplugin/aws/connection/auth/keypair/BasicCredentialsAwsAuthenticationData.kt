package io.confluent.intellijplugin.aws.connection.auth.keypair

import io.confluent.intellijplugin.aws.connection.auth.AuthenticationType
import io.confluent.intellijplugin.aws.connection.auth.common.AwsAuthenticationData
import io.confluent.intellijplugin.aws.ui.external.AwsSettingsInfo

class BasicCredentialsAwsAuthenticationData(private val awsSettingsInfo: AwsSettingsInfo) : AwsAuthenticationData() {
    override val authType: String = AuthenticationType.KEY_PAIR.id

    override fun getCredentialsProvider() = FromCredAttributesBdtAwsCredentialsProvider(awsSettingsInfo)
}