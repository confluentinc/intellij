package io.confluent.intellijplugin.ccloud.model.response

/**
 * Placeholder types for data plane operations (to be implemented).
 * this file will be deleted when the data plane operations are implemented.
 */

data class CreateTopicRequest(val topicName: String)
data class TopicDetails(val topicName: String)
data class PartitionData(val partitionId: Int)

data class ProduceRequest(val value: String)
data class ProduceResponse(val success: Boolean)
data class ConsumeRequest(val maxRecords: Int)
data class ConsumerRecord(val key: String?, val value: String)

data class ConsumerGroupData(val groupId: String, val state: String)
data class ConsumerGroupDetails(val groupId: String)
