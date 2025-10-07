package io.confluent.intellijplugin.settings

data class KafkaSslConfig(
    val validateHostName: Boolean,
    val truststoreLocation: String,
    val truststorePassword: String,
    val useKeyStore: Boolean,
    val keystoreLocation: String,
    val keystorePassword: String,
    val keyPassword: String,

    val accessCertificate: String,
    val accessKey: String,
    val caCertificate: String
)