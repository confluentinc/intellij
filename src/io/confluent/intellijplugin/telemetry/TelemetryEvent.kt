package io.confluent.intellijplugin.telemetry

/**
 * Interface for all telemetry events to enforce type safety at compile time.
 *
 * Each event type must implement this interface and provide:
 * - eventName: The name that appears in Segment (e.g., "TopicCreateAction")
 * - properties(): Map of event-specific properties
 */
internal interface TelemetryEvent {
    val eventName: String
    fun properties(): Map<String, Any>
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
) : TelemetryEvent {
    override val eventName = "Action Invoked"

    override fun properties() = buildMap<String, Any> {
        put("action", actionName)
        put("actionClass", actionClassName)
        put("invokedPlace", invokedPlace)
        if (registeredActionId != null) { put("registeredActionId", registeredActionId) }
    }
}

/**
 * Tracks when a user makes connection attempts.
 *
 * @param action Action type of the connection attempt ("Create", "Test")
 * @param brokerConfigurationSource Broker configuration source ("CLOUD", "FROM_UI", "FROM_PROPERTIES")
 * @param schemaRegistryType Type of schema registry ("Confluent", "AWS Glue", "None")
 * @param withSshTunnel Whether SSH tunnel is enabled
 * @param kafkaAuthMethod Kafka authentication method used ("None", "SASL", "SSL", "Anonymous")
 * @param success Whether the connection was successfully created
 * @param errorType Error type if connection failed
 * @param propertySource Property source ("DIRECT", "FILE") if broker configuration is "FROM_PROPERTIES"
 * @param cloudType Cloud provider if broker configuration is "CLOUD" ("Confluent", "AWS")
 */
data class ConnectionEvent(
    val action: String,
    val brokerConfigurationSource: String,
    val schemaRegistryType: String,
    val withSshTunnel: Boolean,
    val kafkaAuthMethod: String,
    val success: Boolean,
    val propertySource: String? = null,
    val cloudType: String? = null,
    val hasCCloudDomain: Boolean? = null,
    val errorType: String? = null,
) : TelemetryEvent {
    override val eventName = "Connection Action"

    override fun properties() = buildMap<String, Any> {
        put("action", action)
        put("brokerConfigurationSource", brokerConfigurationSource)
        put("schemaRegistryType", schemaRegistryType)
        put("withSshTunnel", withSshTunnel)
        put("kafkaAuthMethod", kafkaAuthMethod)
        put("success", success)
        propertySource?.let { put("propertySource", it) }
        cloudType?.let { put("cloudType", it) }
        hasCCloudDomain?.let { put("hasCCloudDomain", it) }
        errorType?.let { put("errorType", it) }
    }
}

/**
 * Tracks Confluent Cloud authentication events.
 *
 * @param status Authentication status ("signed in", "signed out", "authentication failed")
 * @param errorType Error type if authentication failed
 */
data class CCloudAuthenticationEvent(
    val status: String,
    val errorType: String? = null,
) : TelemetryEvent {
    override val eventName = "CCloud Authentication"

    override fun properties() = buildMap<String, Any> {
        put("status", status)
        errorType?.let { put("errorType", it) }
    }
}

// TODO: Define expected properties for all tracked events

object TopicCreateAction : TelemetryEvent {
    override val eventName = "TopicCreateAction"
    override fun properties() = emptyMap<String, Any>()
}

object TopicDeleteAction : TelemetryEvent {
    override val eventName = "TopicDeleteAction"
    override fun properties() = emptyMap<String, Any>()
}

object TopicClearAction : TelemetryEvent {
    override val eventName = "TopicClearAction"
    override fun properties() = emptyMap<String, Any>()
}

object ProducerOpenAction : TelemetryEvent {
    override val eventName = "ProducerOpenAction"
    override fun properties() = emptyMap<String, Any>()
}

object ProducerKeyValueAction : TelemetryEvent {
    override val eventName = "ProducerKeyValueAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerOpenAction : TelemetryEvent {
    override val eventName = "ConsumerOpenAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerKeyValueAction : TelemetryEvent {
    override val eventName = "ConsumerKeyValueAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerGroupChangeOffsetAction : TelemetryEvent {
    override val eventName = "ConsumerGroupChangeOffsetAction"
    override fun properties() = emptyMap<String, Any>()
}

object ConsumerGroupDeleteAction : TelemetryEvent {
    override val eventName = "ConsumerGroupDeleteAction"
    override fun properties() = emptyMap<String, Any>()
}

object PartitionsClearAction : TelemetryEvent {
    override val eventName = "PartitionsClearAction"
    override fun properties() = emptyMap<String, Any>()
}

/**
 * Tracks plugin activation at IDE startup.
 * Sent once per IDE session when the plugin is first activated.
 */
object PluginActivatedEvent : TelemetryEvent {
    override val eventName = "Plugin Activated"
    override fun properties() = emptyMap<String, Any>()
}
