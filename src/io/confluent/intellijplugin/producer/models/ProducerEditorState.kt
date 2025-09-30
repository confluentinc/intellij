package io.confluent.intellijplugin.producer.models

import io.confluent.intellijplugin.common.settings.StorageProducerConfig
import io.confluent.intellijplugin.consumer.editor.KafkaRecord

data class ProducerEditorState(val output: List<KafkaRecord>,
                               val config: StorageProducerConfig)