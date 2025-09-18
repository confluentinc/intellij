package com.jetbrains.bigdatatools.kafka.aws.connection.auth

import com.jetbrains.bigdatatools.kafka.aws.connection.auth.anon.AnonAwsAuthenticationData
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.common.AwsAuthenticationData
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.defaultauth.DefaultAwsAuthenticationData
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.keypair.BasicCredentialsAwsAuthenticationData
import com.jetbrains.bigdatatools.kafka.aws.connection.auth.profile.ProfileFromConfigAwsAuthenticationData
import com.jetbrains.bigdatatools.kafka.aws.ui.external.AwsSettingsInfo


object AwsAuthUtil {
  fun getPrimaryAuthentication(awsInfo: AwsSettingsInfo): AwsAuthenticationData = when (AuthenticationType.getById(awsInfo.authenticationType)) {
    AuthenticationType.KEY_PAIR -> BasicCredentialsAwsAuthenticationData(awsInfo)
    AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE -> ProfileFromConfigAwsAuthenticationData(awsInfo)
    AuthenticationType.ANON -> AnonAwsAuthenticationData()
    else -> DefaultAwsAuthenticationData()
  }
}