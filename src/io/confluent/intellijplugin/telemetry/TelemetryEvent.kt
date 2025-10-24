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

/**
 * Tracks action invocations from the Kafka plugin for discovery.
 * This is automatically tracked by ActionTelemetryListener.
 *
 * @param actionName The normalized action ID
 * @param actionClassName The responsible class for the action (e.g. "CreateNewConnectionAction")
 * @param registeredActionId The action ID from plugin.xml (e.g., "kafka.CreateTopicAction"), null for inner classes
 * @param invokedPlace Where the action was invoked from (e.g., "MainMenu", "EditorPopup")
 *
 */
data class ActionInvokedEvent(
    val actionName: String,
    val actionClassName: String,
    val registeredActionId: String? = null,
    val invokedPlace: String,
) : TelemetryEvent() {
    override val eventName = "Action Invoked"

    override fun properties() = buildMap<String, Any> {
        put("action", actionName)
        put("actionClass", actionClassName)
        put("invokedPlace", invokedPlace)
        if (registeredActionId != null) { put("registeredActionId", registeredActionId) }
    }
}

/**
 * Tracks when a user creates a new Kafka connection.
 *
 * @param connectionType Type of connection (e.g., "Kafka", "Confluent Cloud")
 * @param cloudType Cloud provider if applicable (e.g., "Confluent", "AWS", "Local")
 * @param hasSchemaRegistry Whether schema registry is configured
 * @param registryType Type of schema registry ("Confluent", "AWS Glue", "None")
 * @param hasSshTunnel Whether SSH tunnel is enabled
 * @param authMethod Authentication method used ("None", "SASL", "SSL", etc.)
 * @param success Whether the connection was successfully created
 * @param errorType Error type if connection failed
 */
data class ConnectionCreatedEvent(
    val connectionType: String,
    val cloudType: String,
    val hasSchemaRegistry: Boolean,
    val registryType: String,
    val hasSshTunnel: Boolean,
    val authMethod: String,
    val success: Boolean,
    val errorType: String? = null, // Error type if connection failed
) : TelemetryEvent() {
    override val eventName = "Connection Created"

    override fun properties() = buildMap<String, Any> {
        put("connectionType", connectionType)
        put("cloudType", cloudType)
        put("hasSchemaRegistry", hasSchemaRegistry)
        put("registryType", registryType)
        put("hasSshTunnel", hasSshTunnel)
        put("authMethod", authMethod)
        put("success", success)
        errorType?.let { put("errorType", it) }
    }
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
