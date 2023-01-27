package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaSaslMechanism(val title: String) {
  PLAIN(KafkaMessagesBundle.message("kafka.auth.type.plain")),
  SCRAM_512(KafkaMessagesBundle.message("kafka.auth.type.scram512")),
  SCRAM_256(KafkaMessagesBundle.message("kafka.auth.type.scram256")),
  KERBEROS(KafkaMessagesBundle.message("kafka.auth.type.kerberos"));
}
