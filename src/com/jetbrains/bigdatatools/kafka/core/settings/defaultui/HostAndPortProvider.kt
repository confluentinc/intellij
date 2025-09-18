package com.jetbrains.bigdatatools.kafka.core.settings.defaultui

interface HostAndPortProvider {
  fun registerChangeListener(listener: HostAndPortChangeListener)
}

interface HostAndPortChangeListener {
  fun onChange()
}