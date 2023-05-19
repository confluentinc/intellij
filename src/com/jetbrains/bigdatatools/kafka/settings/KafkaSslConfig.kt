package com.jetbrains.bigdatatools.kafka.settings

data class KafkaSslConfig(
  val validateHostName: Boolean,
  val truststoreLocation: String,
  val truststorePassword: String,
  val useKeyStore: Boolean,
  val keystoreLocation: String,
  val keystorePassword: String,
  val keyPassword: String,
)