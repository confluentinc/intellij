package com.jetbrains.bigdatatools.kafka.model


data class InternalSegmentSizeDto(val internalTopicWithSegmentSize: Map<String, TopicPresentable> = emptyMap(),
                                  val clusterMetricsWithSegmentSize: InternalClusterMetrics = InternalClusterMetrics())