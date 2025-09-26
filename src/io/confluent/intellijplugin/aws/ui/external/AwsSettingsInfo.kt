package io.confluent.intellijplugin.aws.ui.external

interface AwsSettingsInfo {
  val authenticationType: String

  val profile: String?

  val accessKey: String?
  val secretKey: String?

  val region: String?
  val customCredentialPath: String?
  val customConfigPath: String?
}