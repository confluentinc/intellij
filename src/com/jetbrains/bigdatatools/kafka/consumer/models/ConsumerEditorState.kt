package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord

data class ConsumerEditorState(val output: List<Result<ConsumerRecord<Any, Any>>>,
                               val config: StorageConsumerConfig)