package io.confluent.intellijplugin.rfs

import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.apache.kafka.common.config.SaslConfigs.GSSAPI_MECHANISM

enum class KafkaSaslMechanism(val title: String, val saslMechanism: String?, val module: String) {
  PLAIN(KafkaMessagesBundle.message("kafka.auth.type.plain"), "PLAIN", "org.apache.kafka.common.security.plain.PlainLoginModule"),
  SCRAM_512(KafkaMessagesBundle.message("kafka.auth.type.scram512"), "SCRAM-SHA-512",
            "org.apache.kafka.common.security.scram.ScramLoginModule"),
  SCRAM_256(KafkaMessagesBundle.message("kafka.auth.type.scram256"), "SCRAM-SHA-256",
            "org.apache.kafka.common.security.scram.ScramLoginModule"),
  KERBEROS(KafkaMessagesBundle.message("kafka.auth.type.kerberos"), GSSAPI_MECHANISM, "com.sun.security.auth.module.Krb5LoginModule");
}
