package io.confluent.kafka.consumer.models

import io.confluent.kafka.common.settings.StorageConsumerConfig
import io.confluent.kafka.consumer.editor.KafkaRecord

data class ConsumerEditorState(val output: List<KafkaRecord>,
                               val config: StorageConsumerConfig)