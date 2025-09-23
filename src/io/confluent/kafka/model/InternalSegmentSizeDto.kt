package io.confluent.kafka.model


data class InternalSegmentSizeDto(val internalTopicWithSegmentSize: Map<String, TopicPresentable> = emptyMap(),
                                  val clusterMetricsWithSegmentSize: InternalClusterMetrics = InternalClusterMetrics())