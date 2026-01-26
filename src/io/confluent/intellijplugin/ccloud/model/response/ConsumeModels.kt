package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request/response models for the CCloud consume API.
 */

@Serializable
data class ConsumeRecordsRequest(
    @SerialName("offsets")
    val offsets: List<PartitionOffset>? = null,

    /**
     * Maximum number of records to return across all partitions.
     * Default: 500
     */
    @SerialName("max_poll_records")
    val maxPollRecords: Int? = null,

    /**
     * Overall limit for the sum of sizes of returned records.
     * Default: 52428800 (50MB)
     * Note: Not strictly enforced - first batch may exceed limit.
     */
    @SerialName("fetch_max_bytes")
    val fetchMaxBytes: Int? = null,

    /**
     * If true and offsets not provided, seek to beginning.
     * If false/missing and offsets not provided, seek to end.
     */
    @SerialName("from_beginning")
    val fromBeginning: Boolean? = null
)

/**
 * Partition and offset pair for specifying consume position.
 */
@Serializable
data class PartitionOffset(
    @SerialName("partition_id")
    val partitionId: Int,

    @SerialName("offset")
    val offset: Long
)

/**
 * Response containing consumed records grouped by partition.
 */
@Serializable
data class ConsumeRecordsResponse(
    @SerialName("cluster_id")
    val clusterId: String,

    @SerialName("topic_name")
    val topicName: String,

    @SerialName("partition_data_list")
    val partitionDataList: List<PartitionConsumeData>
)

/**
 * Consumed records for a single partition.
 */
@Serializable
data class PartitionConsumeData(
    @SerialName("partition_id")
    val partitionId: Int,

    /**
     * The next offset to use in subsequent consume requests.
     * Use this to continue consuming from where you left off.
     */
    @SerialName("next_offset")
    val nextOffset: Long,

    @SerialName("records")
    val records: List<PartitionConsumeRecord>
)

/**
 * A single consumed record.
 */
@Serializable
data class PartitionConsumeRecord(
    @SerialName("partition_id") val partitionId: Int,
    @SerialName("offset") val offset: Long,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("timestamp_type") val timestampType: TimestampType,
    @SerialName("headers") val headers: List<PartitionConsumeRecordHeader>,
    @SerialName("key") val key: JsonElement,
    @SerialName("value") val value: JsonElement
)

/**
 * Represents a header of a record.
 *
 * @param key   The key of the header.
 * @param value The value of the header.
 */
@Serializable
data class PartitionConsumeRecordHeader(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String
)

/**
 * @see org.apache.kafka.common.record.TimestampType
 */
enum class TimestampType {
    NO_TIMESTAMP_TYPE,
    CREATE_TIME,
    LOG_APPEND_TIME
}
