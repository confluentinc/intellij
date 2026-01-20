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

/**
 * Tracks user interactions with the message viewer.
 * All message viewer events share the same event name with an "action" discriminator.
 *
 * @param action The action performed (e.g., "start", "stop", "search", "preview-message")
 * @property source Whether the event is from "consumer" or "producer" context
 */
sealed class MessageViewerEvent : TelemetryEvent {
    override val eventName = "Message Viewer Action"
    abstract val action: String
    abstract val source: Source

    /**
     * The source context for message viewer events.
     */
    enum class Source(val value: String) {
        CONSUMER("consumer"),
        PRODUCER("producer")
    }

    /**
     * Tracks when a user starts consuming messages.
     * Captures the configuration options selected when "Start Consuming" is clicked.
     *
     * @param startType The selected start type (e.g., "now", "beginning", "specific_date")
     * @param limitType The selected limit type (e.g., "none", "topic_number_records", "date")
     * @param filterType The selected filter type (e.g., "none", "contains", "regex")
     * @param keyType The selected key deserialization type (e.g., "string", "json", "schema_registry")
     * @param valueType The selected value deserialization type
     * @param hasPartitions Whether specific partitions were specified
     * @param hasConsumerGroup Whether a consumer group was specified
     * @param hasConsumerRecordsLimit Whether consumer records limit was modified from default
     * @param hasRequestTimeoutMs Whether request.timeout.ms was modified from default
     * @param hasMaxPollRecords Whether max.poll.records was modified from default
     * @param hasFetchMaxWaitMs Whether fetch.max.wait.ms was modified from default
     * @param hasFetchMaxBytes Whether fetch.max.bytes was modified from default
     * @param hasMaxPartitionFetchBytes Whether max.partition.fetch.bytes was modified from default
     */
    data class StartConsumer(
        val startType: String,
        val limitType: String,
        val filterType: String,
        val keyType: String,
        val valueType: String,
        val hasPartitions: Boolean = false,
        val hasConsumerGroup: Boolean = false,
        val hasConsumerRecordsLimit: Boolean = false,
        val hasRequestTimeoutMs: Boolean = false,
        val hasMaxPollRecords: Boolean = false,
        val hasFetchMaxWaitMs: Boolean = false,
        val hasFetchMaxBytes: Boolean = false,
        val hasMaxPartitionFetchBytes: Boolean = false,
    ) : MessageViewerEvent() {
        override val source = Source.CONSUMER
        override val action = "start"

        override fun properties() = buildMap<String, Any> {
            put("action", action)
            put("source", source.value)
            put("startType", startType)
            put("limitType", limitType)
            put("filterType", filterType)
            put("keyType", keyType)
            put("valueType", valueType)
            if (hasPartitions) put("hasPartitions", true)
            if (hasConsumerGroup) put("hasConsumerGroup", true)
            if (hasConsumerRecordsLimit) put("hasConsumerRecordsLimit", true)
            if (hasRequestTimeoutMs) put("hasRequestTimeoutMs", true)
            if (hasMaxPollRecords) put("hasMaxPollRecords", true)
            if (hasFetchMaxWaitMs) put("hasFetchMaxWaitMs", true)
            if (hasFetchMaxBytes) put("hasFetchMaxBytes", true)
            if (hasMaxPartitionFetchBytes) put("hasMaxPartitionFetchBytes", true)
        }
    }

    /**
     * Tracks when a user stops consuming/producing messages.
     * Captures the total time spent in the session.
     *
     * @param source Whether invoked from "consumer" or "producer" context
     * @param durationMs Total time in milliseconds from start to stop
     */
    data class Stop(
        override val source: Source,
        val durationMs: Long,
    ) : MessageViewerEvent() {
        override val action = "stop"

        override fun properties() = mapOf<String, Any>(
            "action" to action,
            "source" to source.value,
            "durationMs" to durationMs,
        )
    }

    /**
     * Tracks when a user starts producing messages.
     * Captures the configuration options selected when "Produce" is clicked.
     *
     * @param keyType The selected key serialization type (e.g., "string", "json", "schema_registry")
     * @param valueType The selected value serialization type
     * @param isFlowMode Whether flow mode is enabled (multiple messages)
     * @param generateRandomKeys Whether random key generation is enabled
     * @param generateRandomValues Whether random value generation is enabled
     * @param loadFromCsv Whether loading data from a CSV file
     * @param hasForcePartition Whether a specific partition was forced
     * @param compression The compression type selected
     * @param acks The acknowledgment type selected
     * @param idempotence Whether idempotence is enabled
     */
    data class StartProducer(
        val keyType: String,
        val valueType: String,
        val isFlowMode: Boolean = false,
        val generateRandomKeys: Boolean = false,
        val generateRandomValues: Boolean = false,
        val loadFromCsv: Boolean = false,
        val hasForcePartition: Boolean = false,
        val compression: String,
        val acks: String,
        val idempotence: Boolean,
    ) : MessageViewerEvent() {
        override val source = Source.PRODUCER
        override val action = "start"

        override fun properties() = buildMap<String, Any> {
            put("action", action)
            put("source", source.value)
            put("keyType", keyType)
            put("valueType", valueType)
            put("compression", compression)
            put("acks", acks)
            put("idempotence", idempotence)
            if (isFlowMode) put("isFlowMode", true)
            if (generateRandomKeys) put("generateRandomKeys", true)
            if (generateRandomValues) put("generateRandomValues", true)
            if (loadFromCsv) put("loadFromCsv", true)
            if (hasForcePartition) put("hasForcePartition", true)
        }
    }

    /**
     * Tracks when a user searches/filters in the message viewer table.
     *
     * @param source Whether invoked from "consumer" or "producer" context
     */
    data class Search(override val source: Source) : MessageViewerEvent() {
        override val action = "search"

        override fun properties() = mapOf<String, Any>("action" to action, "source" to source.value)
    }

    /**
     * Tracks when a user clicks on a message row to view its details/preview.
     *
     * @param source Whether invoked from "consumer" or "producer" context
     */
    data class Preview(override val source: Source) : MessageViewerEvent() {
        override val action = "preview-message"

        override fun properties() = mapOf<String, Any>("action" to action, "source" to source.value)
    }
}
