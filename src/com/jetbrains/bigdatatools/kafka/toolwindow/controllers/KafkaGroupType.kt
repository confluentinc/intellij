package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaGroupType(@NlsSafe val title: String) {
  TOPIC(KafkaMessagesBundle.message("settings.topics.tab")),
  CONSUMER_GROUP(KafkaMessagesBundle.message("settings.consumers.tab")),
  SCHEMA_REGISTRY_GROUP(KafkaMessagesBundle.message("settings.registry.tab")),
  TOPIC_DETAIL("Topic details"),
  SCHEMA_DETAIL("Schema details"),
}