package io.confluent.intellijplugin.client

import org.apache.kafka.common.config.TopicConfig.*


object KafkaConstants {
    val TOPIC_DEFAULT_CONFIGS = mapOf(
        CLEANUP_POLICY_CONFIG to CLEANUP_POLICY_DELETE,
        COMPRESSION_TYPE_CONFIG to "producer",
        DELETE_RETENTION_MS_CONFIG to "86400000",
        FILE_DELETE_DELAY_MS_CONFIG to "60000",
        FLUSH_MESSAGES_INTERVAL_CONFIG to "9223372036854775807",
        FLUSH_MS_CONFIG to "9223372036854775807",
        "follower.replication.throttled.replicas" to "",
        INDEX_INTERVAL_BYTES_CONFIG to "4096",
        "leader.replication.throttled.replicas" to "",
        MAX_COMPACTION_LAG_MS_CONFIG to "9223372036854775807",
        MAX_MESSAGE_BYTES_CONFIG to "1000012",
        MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG to "9223372036854775807",
        MESSAGE_TIMESTAMP_TYPE_CONFIG to "CreateTime",
        MIN_CLEANABLE_DIRTY_RATIO_CONFIG to "0.5",
        MIN_COMPACTION_LAG_MS_CONFIG to "0",
        MIN_IN_SYNC_REPLICAS_CONFIG to "1",
        PREALLOCATE_CONFIG to "false",
        RETENTION_BYTES_CONFIG to "-1",
        RETENTION_MS_CONFIG to "604800000",
        SEGMENT_BYTES_CONFIG to "1073741824",
        SEGMENT_INDEX_BYTES_CONFIG to "10485760",
        SEGMENT_JITTER_MS_CONFIG to "0",
        SEGMENT_MS_CONFIG to "604800000",
        UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG to "false",
        MESSAGE_DOWNCONVERSION_ENABLE_CONFIG to "true"
    )
}