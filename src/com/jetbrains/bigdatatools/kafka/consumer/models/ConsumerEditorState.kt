package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaRecord

data class ConsumerEditorState(val output: List<KafkaRecord>,
                               val config: StorageConsumerConfig)