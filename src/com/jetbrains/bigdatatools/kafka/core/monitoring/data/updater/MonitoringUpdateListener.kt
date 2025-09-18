package com.jetbrains.bigdatatools.kafka.core.monitoring.data.updater

import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.DataModel

interface MonitoringUpdateListener {
  fun onStartRefreshConnection()
  fun onStartRefreshModels(id: Int, models: List<DataModel<*>>)
  fun onEnd(id: Int?)
  fun setIntermediate(id: Int, value: Boolean)
  fun setText(id: Int, text: String)
  fun setProgress(id: Int, progress: Double)
}