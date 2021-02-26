package com.jetbrains.bigdatatools.kafka.model


data class InternalSegmentSizeDto(val internalTopicWithSegmentSize: Map<String, InternalTopic> = emptyMap(),
                                  val clusterMetricsWithSegmentSize: InternalClusterMetrics = InternalClusterMetrics())