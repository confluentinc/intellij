package io.confluent.kafka.core.monitoring.data.listener

interface ListenersChangeListener {
  fun lastListenerUnsubscribed()
  fun firstListenerSubscribed()
}