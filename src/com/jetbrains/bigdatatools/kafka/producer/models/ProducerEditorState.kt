package com.jetbrains.bigdatatools.kafka.producer.models

import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaRecord

data class ProducerEditorState(val output: List<KafkaRecord>,
                               val config: StorageProducerConfig)