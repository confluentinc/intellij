package io.confluent.intellijplugin.consumer.models

import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.editor.KafkaRecord

data class ConsumerEditorState(val output: List<KafkaRecord>,
                               val config: StorageConsumerConfig)