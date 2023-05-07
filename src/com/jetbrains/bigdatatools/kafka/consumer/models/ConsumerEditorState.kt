package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.consumer.editor.ConsumerOutputRow

data class ConsumerEditorState(val output: List<ConsumerOutputRow>,
                               val config: StorageConsumerConfig)