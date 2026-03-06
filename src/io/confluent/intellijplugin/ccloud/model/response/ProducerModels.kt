package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request/response models for the CCloud REST produce API (v3).
 * POST /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/records
 */

@Serializable
data class ProduceRecordRequest(
    @SerialName("partition_id")
    val partitionId: Int? = null,

    @SerialName("headers")
    val headers: List<ProduceRecordHeader>? = null,

    @SerialName("key")
    val key: ProduceRecordData? = null,

    @SerialName("value")
    val value: ProduceRecordData? = null,

    @SerialName("timestamp")
    val timestamp: Long? = null
)

@Serializable
data class ProduceRecordData(
    @SerialName("type")
    val type: String,

    @SerialName("data")
    val data: String? = null
)

@Serializable
data class ProduceRecordHeader(
    @SerialName("name")
    val name: String,

    /** Base64-encoded header value bytes. */
    @SerialName("value")
    val value: String? = null
)

@Serializable
data class ProduceRecordResponse(
    @SerialName("error_code")
    val errorCode: Int? = null,

    @SerialName("message")
    val message: String? = null,

    @SerialName("cluster_id")
    val clusterId: String? = null,

    @SerialName("topic_name")
    val topicName: String? = null,

    @SerialName("partition_id")
    val partitionId: Int? = null,

    @SerialName("offset")
    val offset: Long? = null,

    @SerialName("timestamp")
    val timestamp: String? = null,

    @SerialName("key")
    val key: ProduceRecordResponseData? = null,

    @SerialName("value")
    val value: ProduceRecordResponseData? = null
)

@Serializable
data class ProduceRecordResponseData(
    @SerialName("size")
    val size: Int? = null,

    @SerialName("type")
    val type: String? = null
)
