package io.confluent.intellijplugin.telemetry

/**
 * Wrapper function for sending telemetry data to Segment.
 * see @TelemetryService for implementation.
 * see @TelemetryEvent for event definitions.
 */
fun logUsage(event: TelemetryEvent) {
    TelemetryService.getInstance().trackEvent(event.eventName, event.properties())
}

/**
 * Send an identify event to Segment. Should only send once per user on plugin startup and again if user traits change.
 * TODO: Add traits to track
 */
fun sendIdentifyEvent(traits: Map<String, Any>) {
    TelemetryService.getInstance().sendIdentifyEvent(traits)
}
