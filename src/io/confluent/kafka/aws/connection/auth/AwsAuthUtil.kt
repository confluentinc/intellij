package io.confluent.kafka.aws.connection.auth

import io.confluent.kafka.aws.connection.auth.anon.AnonAwsAuthenticationData
import io.confluent.kafka.aws.connection.auth.common.AwsAuthenticationData
import io.confluent.kafka.aws.connection.auth.defaultauth.DefaultAwsAuthenticationData
import io.confluent.kafka.aws.connection.auth.keypair.BasicCredentialsAwsAuthenticationData
import io.confluent.kafka.aws.connection.auth.profile.ProfileFromConfigAwsAuthenticationData
import io.confluent.kafka.aws.ui.external.AwsSettingsInfo


object AwsAuthUtil {
  fun getPrimaryAuthentication(awsInfo: AwsSettingsInfo): AwsAuthenticationData = when (AuthenticationType.getById(awsInfo.authenticationType)) {
    AuthenticationType.KEY_PAIR -> BasicCredentialsAwsAuthenticationData(awsInfo)
    AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE -> ProfileFromConfigAwsAuthenticationData(awsInfo)
    AuthenticationType.ANON -> AnonAwsAuthenticationData()
    else -> DefaultAwsAuthenticationData()
  }
}