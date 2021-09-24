package com.jetbrains.bigdatatools.kafka.consumer.models

data class RunConsumerConfig(val topic: String,
                             val partitions: List<Int>?,
                             val startWith: ConsumerStartWith,
                             val filter: ConsumerFilter,
                             val limit: ConsumerLimit)