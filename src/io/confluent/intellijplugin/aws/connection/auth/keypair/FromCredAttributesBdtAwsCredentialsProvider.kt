package io.confluent.intellijplugin.aws.connection.auth.keypair

import io.confluent.intellijplugin.aws.connection.auth.common.BdtAwsCredentialsProvider
import io.confluent.intellijplugin.aws.credentials.utils.S3MissingCredentialsException
import io.confluent.intellijplugin.aws.credentials.utils.S3MissingSecretKeyException
import io.confluent.intellijplugin.aws.ui.external.AwsSettingsInfo
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider

class FromCredAttributesBdtAwsCredentialsProvider(private val awsSettingsInfo: AwsSettingsInfo) : BdtAwsCredentialsProvider {
  override fun getCredentials(): AwsCredentialsProvider {
    val username = awsSettingsInfo.accessKey?.ifBlank { null } ?: throw S3MissingCredentialsException()
    val password = awsSettingsInfo.secretKey?.ifBlank { null } ?: throw S3MissingSecretKeyException()
    val awsCredentials = AwsBasicCredentials.create(username, password)
    return StaticCredentialsProvider.create(awsCredentials)
  }
}