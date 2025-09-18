package com.jetbrains.bigdatatools.kafka.aws.ui.external

interface AwsSettingsInfo {
  val authenticationType: String

  val profile: String?

  val accessKey: String?
  val secretKey: String?

  val region: String?
  val customCredentialPath: String?
  val customConfigPath: String?
}