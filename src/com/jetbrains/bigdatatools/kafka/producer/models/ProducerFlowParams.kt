package com.jetbrains.bigdatatools.kafka.producer.models

data class ProducerFlowParams(
  val mode: Mode = Mode.MANUAL,
  val flowRecordsCountPerRequest: Int = 1,
  val generateRandomKeys: Boolean = false,
  val generateRandomValues: Boolean = false,
  val requestInterval: Int = 1000,
  val totalRequests: Int = 0,
  val totalElapsedTime: Int = 0,
  val csvFile: String? = null,
)