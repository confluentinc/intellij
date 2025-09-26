package io.confluent.intellijplugin.core.monitoring.toolwindow

interface DetailsMonitoringController<ID> : ComponentController {
  fun setDetailsId(id: ID)
}