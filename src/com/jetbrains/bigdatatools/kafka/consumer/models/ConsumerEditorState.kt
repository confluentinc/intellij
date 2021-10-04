package com.jetbrains.bigdatatools.kafka.consumer.models

import org.apache.kafka.clients.consumer.ConsumerRecord
import java.io.Serializable

data class ConsumerEditorState(val output: List<Result<ConsumerRecord<Serializable, Serializable>>>,
                               val config: RunConsumerConfig)