package com.jetbrains.bigdatatools.kafka.toolwindow.config

class KafkaClusterConfig {
  var isStructure: Boolean = true

  var topicLimit: Int? = 100
  var topicFilterName: String? = null

  var registryLimit: Int? = 100
  var schemaFilterName: String? = null

  var consumerFilterName: String? = null
  var consumerLimit: Int? = 100
}