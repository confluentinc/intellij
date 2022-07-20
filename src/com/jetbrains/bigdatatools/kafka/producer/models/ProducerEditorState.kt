package com.jetbrains.bigdatatools.kafka.producer.models

import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig

data class ProducerEditorState(val output: List<ProducerResultMessage>,
                               val config: StorageProducerConfig)