package io.confluent.kafka.core.monitoring.toolwindow

interface DetailsMonitoringController<ID> : ComponentController {
  fun setDetailsId(id: ID)
}