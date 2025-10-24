package io.confluent.intellijplugin.telemetry

/**
 * Wrapper function for sending telemetry data to Segment.
 * @param TelemetryEvent The type of usage event with defined accompanied properties.
 */
fun logUsage(event: TelemetryEvent) {
    TelemetryService.getInstance().trackEvent(event.eventName, event.properties())
}

/**
 * Wrapper function for sending identify event to Segment. Should only send once per user on plugin startup and again if user traits change.
 * TODO: Add traits to track
 */
fun logUser(traits: Map<String, Any>) {
    TelemetryService.getInstance().sendIdentifyEvent(traits)
}
