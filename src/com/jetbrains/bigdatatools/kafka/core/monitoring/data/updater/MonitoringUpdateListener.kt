package io.confluent.kafka.core.monitoring.data.updater

import io.confluent.kafka.core.monitoring.data.model.DataModel

interface MonitoringUpdateListener {
  fun onStartRefreshConnection()
  fun onStartRefreshModels(id: Int, models: List<DataModel<*>>)
  fun onEnd(id: Int?)
  fun setIntermediate(id: Int, value: Boolean)
  fun setText(id: Int, text: String)
  fun setProgress(id: Int, progress: Double)
}