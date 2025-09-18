package com.jetbrains.bigdatatools.kafka.core.monitoring.data.listener

interface ListenersChangeListener {
  fun lastListenerUnsubscribed()
  fun firstListenerSubscribed()
}