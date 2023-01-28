package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaSaslMechanism(val title: String, val saslMechanism: String?, val module: String) {
  PLAIN(KafkaMessagesBundle.message("kafka.auth.type.plain"), "PLAIN", "org.apache.kafka.common.security.plain.PlainLoginModule"),
  SCRAM_512(KafkaMessagesBundle.message("kafka.auth.type.scram512"), "SCRAM-SHA-512",
            "org.apache.kafka.common.security.scram.ScramLoginModule"),
  SCRAM_256(KafkaMessagesBundle.message("kafka.auth.type.scram256"), "SCRAM-SHA-256",
            "org.apache.kafka.common.security.scram.ScramLoginModule"),
  KERBEROS(KafkaMessagesBundle.message("kafka.auth.type.kerberos"), "GSSAPI", "com.sun.security.auth.module.Krb5LoginModule");
}
