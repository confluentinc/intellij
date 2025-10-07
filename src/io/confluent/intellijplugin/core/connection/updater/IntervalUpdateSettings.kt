package io.confluent.intellijplugin.core.connection.updater

interface IntervalUpdateSettings {
    var dataUpdateIntervalMillis: Int
    var selectedConnectionId: String?

    val configs: MutableMap<String, out Any>
}