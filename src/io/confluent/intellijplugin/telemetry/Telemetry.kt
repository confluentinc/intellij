package io.confluent.intellijplugin.telemetry

import io.confluent.intellijplugin.rfs.KafkaConnectionData

/**
 * Wrapper function for sending telemetry data to Segment.
 * @param TelemetryEvent The type of usage event with defined accompanied properties.
 */
internal fun logUsage(event: TelemetryEvent) {
    TelemetryService.getInstance().sendTrackEvent(event.eventName, event.properties())
}

/**
 * Wrapper function for sending identify event to Segment. Should only send once per user on plugin startup and again if user traits change.
 * TODO: Add traits to track
 */
internal fun logUser(traits: Map<String, Any>) {
    TelemetryService.getInstance().sendIdentifyEvent(traits)
}

/**
 * Utility functions for telemetry tracking.
 */

/**
 * Determines the authentication method used for a Kafka connection.
 */
fun determineAuthMethod(connectionData: KafkaConnectionData): String {
    val properties = connectionData.secretProperties
    val hasSasl = properties.contains("sasl.mechanism")
    val hasSsl = properties.contains("ssl.keystore")

    return when {
        hasSasl && hasSsl -> "SASL_SSL"
        hasSasl -> "SASL"
        hasSsl -> "SSL"
        connectionData.anonymous -> "Anonymous"
        else -> "None"
    }
}
