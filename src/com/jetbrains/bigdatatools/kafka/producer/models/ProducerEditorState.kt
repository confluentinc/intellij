package com.jetbrains.bigdatatools.kafka.producer.models

data class ProducerEditorState(val output: List<ProducerResultMessage>,
                               val config: RunProducerConfig)