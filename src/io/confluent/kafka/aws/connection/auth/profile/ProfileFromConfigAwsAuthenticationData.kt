package io.confluent.kafka.aws.connection.auth.profile

import io.confluent.kafka.aws.connection.auth.AuthenticationType
import io.confluent.kafka.aws.connection.auth.common.AwsAuthenticationData
import io.confluent.kafka.aws.connection.auth.common.BdtAwsCredentialsProvider
import io.confluent.kafka.aws.credentials.profiles.ProfileCredentialProviderFactory
import io.confluent.kafka.aws.settings.AwsCompatibleConnectionData
import io.confluent.kafka.aws.ui.external.AwsSettingsInfo


class ProfileFromConfigAwsAuthenticationData(private val awsInfo: AwsSettingsInfo) : AwsAuthenticationData() {
  override val authType: String = AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE.id
  override fun getCredentialsProvider(): BdtAwsCredentialsProvider = object : BdtAwsCredentialsProvider {
    override fun getCredentials() = ProfileCredentialProviderFactory.instance.getOrCreate(
      awsInfo.profile?.ifBlank { AwsCompatibleConnectionData.DEFAULT_PROFILE_NAME } ?: AwsCompatibleConnectionData.DEFAULT_PROFILE_NAME,
      awsInfo.customConfigPath?.ifBlank { null },
      awsInfo.customCredentialPath?.ifBlank { null })
  }
}