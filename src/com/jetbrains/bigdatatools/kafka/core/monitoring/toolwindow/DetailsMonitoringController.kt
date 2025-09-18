package com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow

interface DetailsMonitoringController<ID> : ComponentController {
  fun setDetailsId(id: ID)
}