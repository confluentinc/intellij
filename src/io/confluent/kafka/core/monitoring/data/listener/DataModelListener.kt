package io.confluent.kafka.core.monitoring.data.listener

interface DataModelListener {
  fun beforeChanged() {}
  fun onChanged() {}
  fun onChangedNonEdt() {}
  fun onError(msg: String, e: Throwable?) {}
  fun onLoadMore() {}
  fun onErrorAdditionalLoad(throwable: Throwable?) {}
  fun onLoadMoreNonEdt() {}
}