package io.confluent.intellijplugin.telemetry

/**
 * Sealed class for all telemetry events to enforce type safety at compile time.
 *
 * Each event type must extend this class and provide:
 * - eventName: The name that appears in Segment (e.g., "TopicCreateAction")
 * - properties(): Map of event-specific properties
 */
sealed class TelemetryEvent {

    abstract val eventName: String

    abstract fun properties(): Map<String, Any>
}

// TODO: Define expected properties for all tracked events

object TopicCreateAction : TelemetryEvent() {
    override val eventName = "TopicCreateAction"
    override fun properties() = emptyMap<String, Any>()
}

object TopicDeleteAction : TelemetryEvent() {
    override val eventName = "TopicDeleteAction"
    override fun properties() = emptyMap<String, Any>()
}

object TopicClearAction : TelemetryEvent() {
    override val eventName = "TopicClearAction"
    override fun properties() = emptyMap<String, Any>()
}

object ProducerOpenAction : TelemetryEvent() {
    override val eventName = "ProducerOpenAction"
    override fun properties() = emptyMap<String, Any>()
}

object ProducerKeyValueAction : TelemetryEvent() {
    override val eventName = "ProducerKeyValueAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerOpenAction : TelemetryEvent() {
    override val eventName = "ConsumerOpenAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerKeyValueAction : TelemetryEvent() {
    override val eventName = "ConsumerKeyValueAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerGroupChangeOffsetAction : TelemetryEvent() {
    override val eventName = "ConsumerGroupChangeOffsetAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerGroupDeleteAction : TelemetryEvent() {
    override val eventName = "ConsumerGroupDeleteAction"
    override fun properties() = emptyMap<String, Any>()
}

object PartitionsClearAction : TelemetryEvent() {
    override val eventName = "PartitionsClearAction"
    override fun properties() = emptyMap<String, Any>()
}

