package com.jetbrains.bigdatatools.kafka.toolwindow.config

import java.io.Serializable

class KafkaClusterConfig : Serializable {
  var showSoftDeleted: Boolean = false
  var isStructure: Boolean = true

  var topicLimit: Int? = 100
  var topicFilterName: String? = null

  var registryLimit: Int? = 100
  var schemaFilterName: String? = null

  var consumerFilterName: String? = null
  var consumerLimit: Int? = 100

  var topicsPined: MutableSet<String> = mutableSetOf()
}