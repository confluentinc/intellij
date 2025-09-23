package io.confluent.kafka.model


data class InternalReplica(val broker: Int = 0,
                           val leader: Boolean = false,
                           val inSync: Boolean = false)