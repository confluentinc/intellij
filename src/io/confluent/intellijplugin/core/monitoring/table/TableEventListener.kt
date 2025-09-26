package io.confluent.intellijplugin.core.monitoring.table

interface TableEventListener {
  fun beforeChanged() {}
  fun onChanged() {}
  fun onError(msg: String, e: Throwable? = null) {}
}