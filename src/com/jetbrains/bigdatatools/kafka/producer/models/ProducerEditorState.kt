package io.confluent.kafka.producer.models

import io.confluent.kafka.common.settings.StorageProducerConfig
import io.confluent.kafka.consumer.editor.KafkaRecord

data class ProducerEditorState(val output: List<KafkaRecord>,
                               val config: StorageProducerConfig)