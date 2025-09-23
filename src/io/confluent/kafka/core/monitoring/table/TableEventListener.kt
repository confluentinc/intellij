package io.confluent.kafka.core.monitoring.table

interface TableEventListener {
  fun beforeChanged() {}
  fun onChanged() {}
  fun onError(msg: String, e: Throwable? = null) {}
}