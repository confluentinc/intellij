package io.confluent.intellijplugin.toolwindow.config

data class KafkaClusterConfig(
  var isStructure: Boolean = true,

  var topicLimit: Int? = 100,
  var topicFilterName: String? = null,

  var registryLimit: Int? = 100,
  var schemaFilterName: String? = null,

  var consumerLimit: Int? = 100,
  var consumerFilterName: String? = null,

  var topicsPined: MutableSet<String> = mutableSetOf(),
  var schemasPined: MutableSet<String> = mutableSetOf(),
  var consumerGroupPined: MutableSet<String> = mutableSetOf()
)