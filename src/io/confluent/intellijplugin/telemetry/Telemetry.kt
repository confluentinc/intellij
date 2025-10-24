package io.confluent.intellijplugin.telemetry

import io.confluent.intellijplugin.rfs.KafkaConnectionData

/**
 * Wrapper function for sending telemetry data to Segment.
 * @param TelemetryEvent The type of usage event with defined accompanied properties.
 */
fun logUsage(event: TelemetryEvent) {
    TelemetryService.getInstance().sendTrackEvent(event.eventName, event.properties())
}

/**
 * Wrapper function for sending identify event to Segment. Should only send once per user on plugin startup and again if user traits change.
 * TODO: Add traits to track
 */
fun logUser(traits: Map<String, Any>) {
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
    return when {
        properties.contains("sasl.mechanism") -> "SASL"
        properties.contains("ssl.keystore") || properties.contains("ssl.truststore") -> "SSL"
        connectionData.anonymous -> "Anonymous"
        else -> "None"
    }
}