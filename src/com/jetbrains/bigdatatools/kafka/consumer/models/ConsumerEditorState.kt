package com.jetbrains.bigdatatools.kafka.consumer.models

import org.apache.kafka.clients.consumer.ConsumerRecord

data class ConsumerEditorState(val output: List<Result<ConsumerRecord<Any, Any>>>,
                               val config: RunConsumerConfig)