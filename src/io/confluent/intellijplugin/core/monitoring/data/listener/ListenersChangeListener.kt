package io.confluent.intellijplugin.core.monitoring.data.listener

interface ListenersChangeListener {
  fun lastListenerUnsubscribed()
  fun firstListenerSubscribed()
}