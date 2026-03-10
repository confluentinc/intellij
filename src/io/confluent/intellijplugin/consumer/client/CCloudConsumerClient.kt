package io.confluent.intellijplugin.consumer.client

import io.confluent.intellijplugin.ccloud.client.CCloudApiException
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsRequest
import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsResponse
import io.confluent.intellijplugin.ccloud.model.response.PartitionConsumeRecord
import io.confluent.intellijplugin.ccloud.model.response.PartitionOffset
import io.confluent.intellijplugin.ccloud.model.response.TimestampType as ApiTimestampType
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.editor.ConsumerEditorUtils
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.util.KafkaOffsetUtils
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.MessageIndexes
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import com.google.protobuf.DynamicMessage
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.serializers.schema.id.SchemaId
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.VisibleForTesting
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * REST-based consumer client for Confluent Cloud.
 *
 * Uses Confluent Cloud internal REST API with OAuth authentication
 * to consume records from Kafka topics.
 *
 * @param clusterDataManager The cluster data manager for CCloud connection
 * @param onStart Callback invoked when consumption starts
 * @param onStop Callback invoked when consumption stops
 */
class CCloudConsumerClient(
    private val clusterDataManager: CCloudClusterDataManager,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
) : ConsumerClient {

    private val running = AtomicBoolean(false)
    private var pollingJob: Job? = null

    // Use our own independent scope to prevent cancellation from UI operations
    // SupervisorJob ensures child coroutine failures don't cancel the scope
    private var consumerScope: CoroutineScope? = null

    // Track next offsets per partition for subsequent requests
    @VisibleForTesting
    internal val nextOffsets = mutableMapOf<Int, Long>()

    @VisibleForTesting
    internal val schemaCache = ConcurrentHashMap<SchemaRegistryClusterId, ConcurrentHashMap<SchemaCacheKey, ParsedSchema>>()

    @VisibleForTesting
    internal var currentKeyConfig: ConsumerProducerFieldConfig? = null
    @VisibleForTesting
    internal var currentValueConfig: ConsumerProducerFieldConfig? = null

    // Cached deserializers for the current session — created once in start(), reused for all records
    private var keyDeserializer: Deserializer<*>? = null
    private var valueDeserializer: Deserializer<*>? = null

    // Resolved config values for the current session
    @VisibleForTesting
    internal var resolvedMaxPollRecords: Int? = null
    @VisibleForTesting
    internal var resolvedFetchMaxBytes: Int? = null

    override fun start(
        config: StorageConsumerConfig,
        valueConfig: ConsumerProducerFieldConfig,
        keyConfig: ConsumerProducerFieldConfig,
        consume: (Long, List<ConsumerRecord<Any, Any>>) -> Unit,
        timestampUpdate: () -> Unit,
        consumeError: (Throwable, Int?, Long?) -> Unit
    ) {
        // Validate partition filter synchronously before signaling start, so errors propagate
        // to the caller as a popup notification (matching native KafkaConsumerClient behavior)
        // and the UI is never greyed out on validation failure.
        // Safe to use runBlocking here because start() is called from a pooled thread, not EDT.
        val fetcher = clusterDataManager.getDataPlaneCache().getFetcher()
            ?: throw IllegalStateException("DataPlaneFetcher not initialized")
        val partitionFilter = runBlocking {
            validatePartitionFilter(config.partitions, config.getInnerTopic(), fetcher)
        }

        running.set(true)
        onStart()
        nextOffsets.clear()
        schemaCache.clear()
        currentKeyConfig = keyConfig
        currentValueConfig = valueConfig
        keyDeserializer = createDeserializerOrNull(keyConfig.type)
        valueDeserializer = createDeserializerOrNull(valueConfig.type)

        // Resolve advanced settings from config
        resolvedMaxPollRecords = config.properties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG]?.toIntOrNull()
        resolvedFetchMaxBytes = config.properties[ConsumerConfig.FETCH_MAX_BYTES_CONFIG]?.toIntOrNull()

        // Create a new independent scope for this consumption session
        // Using Dispatchers.IO for network operations
        consumerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        pollingJob = consumerScope!!.launch {
            try {
                pollLoop(config, partitionFilter, consume, timestampUpdate, consumeError)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consumeError(e, null, null)
            } finally {
                running.set(false)
                onStop()
            }
        }
    }

    private suspend fun pollLoop(
        config: StorageConsumerConfig,
        partitionFilter: Set<Int>?,
        consume: (Long, List<ConsumerRecord<Any, Any>>) -> Unit,
        timestampUpdate: () -> Unit,
        consumeError: (Throwable, Int?, Long?) -> Unit
    ) {
        val fetcher = clusterDataManager.getDataPlaneCache().getFetcher()
            ?: throw IllegalStateException("DataPlaneFetcher not initialized")

        val topicName = config.getInnerTopic()
        val limit = config.getLimit()
        var totalRecordsConsumed = 0L
        var totalBytesConsumed = 0L
        var isFirstRequest = true
        var consecutiveErrors = 0

        // Per-partition tracking for partition-level limits
        val partitionRecordCounts = mutableMapOf<Int, Long>()
        val partitionByteCounts = mutableMapOf<Int, Long>()

        while (running.get() && (consumerScope?.isActive == true)) {
            val startTime = System.currentTimeMillis()

            try {
                val response = if (isFirstRequest) {
                    fetchInitialRecords(config, fetcher, topicName, partitionFilter)
                } else {
                    fetcher.consumeRecords(topicName, buildSubsequentConsumeRequest())
                }
                isFirstRequest = false
                val pollTime = System.currentTimeMillis() - startTime

                // Reset error counter on successful request
                consecutiveErrors = 0

                timestampUpdate()

                updateNextOffsets(response, partitionFilter)

                // Flatten all records from matching partitions; deserialization failures are reported
                // as error rows in the consumer output via consumeError() without stopping consumption
                val filteredPartitionData = if (partitionFilter != null) {
                    response.partitionDataList.filter { it.partitionId in partitionFilter }
                } else {
                    response.partitionDataList
                }
                val allRecords = filteredPartitionData.flatMap { partitionData ->
                    partitionData.records.mapNotNull { record ->
                        try {
                            convertToConsumerRecord(record, topicName, fetcher)
                        } catch (e: Exception) {
                            consumeError(e, record.partitionId, record.offset)
                            null
                        }
                    }
                }

                if (allRecords.isEmpty()) {
                    // No new records, back off before next poll
                    delay(EMPTY_POLL_DELAY_MS)
                    continue
                }

                // Apply filters (client-side)
                val filter = config.getFilter()
                val filteredRecords = allRecords.filter { filter.isRecordPassFilter(it) }

                if (filteredRecords.isNotEmpty()) {
                    consume(pollTime, filteredRecords)

                    // Update topic-level counts
                    totalRecordsConsumed += filteredRecords.size
                    val batchBytes = filteredRecords.sumOf { getRecordSize(it) }
                    totalBytesConsumed += batchBytes

                    // Update per-partition counts
                    filteredRecords.forEach { record ->
                        val partition = record.partition()
                        partitionRecordCounts[partition] = (partitionRecordCounts[partition] ?: 0L) + 1
                        partitionByteCounts[partition] = (partitionByteCounts[partition] ?: 0L) + getRecordSize(record)
                    }
                }

                // Check topic record count limit
                if (limit.topicRecordsCount != null && totalRecordsConsumed >= limit.topicRecordsCount) {
                    break
                }

                // Check topic max size limit
                if (limit.topicRecordsSize != null && totalBytesConsumed >= limit.topicRecordsSize) {
                    break
                }

                // Check partition record count limit
                if (limit.partitionRecordsCount != null) {
                    val allPartitionsReachedLimit = partitionRecordCounts.isNotEmpty() &&
                        partitionRecordCounts.values.all { it >= limit.partitionRecordsCount }
                    if (allPartitionsReachedLimit) {
                        break
                    }
                }

                // Check partition max size limit
                if (limit.partitionRecordsSize != null) {
                    val allPartitionsReachedLimit = partitionByteCounts.isNotEmpty() &&
                        partitionByteCounts.values.all { it >= limit.partitionRecordsSize }
                    if (allPartitionsReachedLimit) {
                        break
                    }
                }

                // Check time limit
                if (limit.time != null) {
                    val latestTimestamp = allRecords.maxOfOrNull { it.timestamp() } ?: 0L
                    if (latestTimestamp > limit.time) {
                        break
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consumeError(e, null, null)

                val statusCode = (e as? CCloudApiException)?.statusCode
                if (statusCode != null && !isRetryableStatus(statusCode)) {
                    break
                }

                consecutiveErrors++

                if (statusCode == 401) {
                    // Token may have expired - wait for token refresh service
                    delay(TOKEN_REFRESH_DELAY_MS)
                } else {
                    // Apply exponential backoff for retryable errors
                    val backoffMs = min(
                        BASE_BACKOFF_MS * (1L shl min(consecutiveErrors, 5)),
                        MAX_BACKOFF_MS
                    )
                    delay(backoffMs)
                }
            }
        }
    }

    @VisibleForTesting
    internal fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode == 401 || statusCode == 429 || statusCode in 500..599

    /**
     * Calculate the approximate size of a record in bytes.
     * Uses the serialized sizes if available, otherwise estimates from the value content.
     */
    @VisibleForTesting
    internal fun getRecordSize(record: ConsumerRecord<Any, Any>): Long {
        val keySize = if (record.serializedKeySize() >= 0) {
            record.serializedKeySize().toLong()
        } else {
            record.key()?.toString()?.toByteArray()?.size?.toLong() ?: 0L
        }

        val valueSize = if (record.serializedValueSize() >= 0) {
            record.serializedValueSize().toLong()
        } else {
            record.value()?.toString()?.toByteArray()?.size?.toLong() ?: 0L
        }

        return keySize + valueSize
    }

    /**
     * Validate user-specified partitions against the actual topic partitions.
     * Intersects the user input with real partitions and errors if no valid partitions remain.
     *
     * @return the validated partition filter set, or null if no filter was specified.
     */
    @VisibleForTesting
    internal suspend fun validatePartitionFilter(
        partitionsText: String?,
        topicName: String,
        fetcher: DataPlaneFetcher
    ): Set<Int>? {
        val parsed = ConsumerEditorUtils.parsePartitionsText(partitionsText).toSet()
        if (parsed.isEmpty()) return null

        val actualPartitions = fetcher.describeTopicPartitions(topicName)
            .map { it.partitionId }.toSet()
        val validPartitions = parsed.intersect(actualPartitions)
        if (validPartitions.isEmpty()) {
            error(KafkaMessagesBundle.message("consumer.partition.not.found", topicName))
        }
        return validPartitions
    }

    /**
     * Fetch initial records, choosing the optimal path based on whether partitions are filtered.
     *
     * With a partition filter:
     * - Offset-based types resolve offsets only for filtered partitions (via [getPartitionOffset]),
     *   then use the POST endpoint with those specific offsets.
     * - Timestamp-based types use the single-partition GET endpoint in parallel, because the
     *   POST endpoint's `timestamp` parameter applies to ALL partitions.
     *
     * Without a partition filter, uses the standard multi-partition POST endpoint directly.
     */
    @VisibleForTesting
    internal suspend fun fetchInitialRecords(
        config: StorageConsumerConfig,
        fetcher: DataPlaneFetcher,
        topicName: String,
        partitionFilter: Set<Int>?
    ): ConsumeRecordsResponse {
        if (partitionFilter == null) {
            return fetcher.consumeRecords(topicName, buildInitialConsumeRequest(config, fetcher))
        }

        val startsWith = config.getStartsWith()
        return when (startsWith.type) {
            ConsumerStartType.THE_BEGINNING -> {
                val offsets = resolveOffsetsForPartitions(fetcher, topicName, partitionFilter, fromBeginning = true)
                fetcher.consumeRecords(topicName, consumeRequest(
                    offsets = offsets.map { (pid, off) -> PartitionOffset(pid, off) }
                ))
            }

            ConsumerStartType.NOW -> {
                val offsets = resolveOffsetsForPartitions(fetcher, topicName, partitionFilter, fromBeginning = false)
                fetcher.consumeRecords(topicName, consumeRequest(
                    offsets = offsets.map { (pid, off) -> PartitionOffset(pid, off) }
                ))
            }

            ConsumerStartType.OFFSET -> {
                val userOffset = startsWith.offset ?: 0L
                val offsets = resolveOffsetsForPartitions(fetcher, topicName, partitionFilter, fromBeginning = true)
                fetcher.consumeRecords(topicName, consumeRequest(
                    offsets = offsets.map { (pid, off) -> PartitionOffset(pid, off + userOffset) }
                ))
            }

            ConsumerStartType.LATEST_OFFSET_MINUS_X -> {
                val userOffset = startsWith.offset ?: 0L
                val offsets = resolveOffsetsForPartitions(fetcher, topicName, partitionFilter, fromBeginning = false)
                fetcher.consumeRecords(topicName, consumeRequest(
                    offsets = offsets.map { (pid, off) -> PartitionOffset(pid, max(0, off + userOffset)) }
                ))
            }

            ConsumerStartType.SPECIFIC_DATE,
            ConsumerStartType.LAST_HOUR,
            ConsumerStartType.TODAY,
            ConsumerStartType.YESTERDAY -> {
                // Must use single-partition GET: POST timestamp applies to ALL partitions
                val timestamp = KafkaOffsetUtils.calculateStartTime(startsWith)
                    ?: error("Failed to calculate start time for ${startsWith.type}")
                fetchPartitionsByTimestamp(fetcher, topicName, partitionFilter, timestamp)
            }

            ConsumerStartType.CONSUMER_GROUP ->
                error("Consumer group start type is not yet supported for CCloud connections")
        }
    }

    /**
     * Build the initial consume request for the multi-partition POST endpoint (unfiltered only).
     */
    @VisibleForTesting
    internal suspend fun buildInitialConsumeRequest(
        config: StorageConsumerConfig,
        fetcher: DataPlaneFetcher
    ): ConsumeRecordsRequest {
        val startsWith = config.getStartsWith()
        val topicName = config.getInnerTopic()

        return when (startsWith.type) {
            ConsumerStartType.THE_BEGINNING -> consumeRequest(fromBeginning = true)
            ConsumerStartType.NOW -> consumeRequest(fromBeginning = false)

            ConsumerStartType.OFFSET -> {
                val offset = startsWith.offset ?: 0L
                val beginningOffsets = fetcher.getTopicBeginningOffsets(topicName)
                consumeRequest(
                    offsets = beginningOffsets.map { (pid, off) -> PartitionOffset(pid, off + offset) }
                )
            }

            ConsumerStartType.LATEST_OFFSET_MINUS_X -> {
                val offset = startsWith.offset ?: 0L
                val endOffsets = fetcher.getTopicEndOffsets(topicName)
                consumeRequest(
                    offsets = endOffsets.map { (pid, off) -> PartitionOffset(pid, max(0, off + offset)) }
                )
            }

            ConsumerStartType.SPECIFIC_DATE,
            ConsumerStartType.LAST_HOUR,
            ConsumerStartType.TODAY,
            ConsumerStartType.YESTERDAY -> consumeRequest(
                timestamp = KafkaOffsetUtils.calculateStartTime(startsWith)
            )

            ConsumerStartType.CONSUMER_GROUP ->
                error("Consumer group start type is not yet supported for CCloud connections")
        }
    }

    /**
     * Helper to create a [ConsumeRecordsRequest] with resolved advanced settings.
     */
    private fun consumeRequest(
        offsets: List<PartitionOffset>? = null,
        fromBeginning: Boolean? = null,
        timestamp: Long? = null
    ) = ConsumeRecordsRequest(
        offsets = offsets,
        fromBeginning = fromBeginning,
        timestamp = timestamp,
        maxPollRecords = resolvedMaxPollRecords,
        fetchMaxBytes = resolvedFetchMaxBytes
    )

    /**
     * Resolve offsets for only the specified partitions in parallel.
     * Uses [DataPlaneFetcher.getPartitionOffset] per partition instead of
     * [DataPlaneFetcher.getTopicBeginningOffsets]/[DataPlaneFetcher.getTopicEndOffsets]
     * which fetch offsets for ALL partitions.
     */
    private suspend fun resolveOffsetsForPartitions(
        fetcher: DataPlaneFetcher,
        topicName: String,
        partitions: Set<Int>,
        fromBeginning: Boolean
    ): Map<Int, Long> = coroutineScope {
        partitions.map { partitionId ->
            async { partitionId to fetcher.getPartitionOffset(topicName, partitionId, fromBeginning) }
        }.awaitAll().toMap()
    }

    /**
     * Fetch records from specific partitions by timestamp using parallel single-partition GET calls.
     * The POST endpoint's `timestamp` parameter applies to ALL partitions, so we must use
     * individual GET calls to target only the filtered partitions.
     */
    private suspend fun fetchPartitionsByTimestamp(
        fetcher: DataPlaneFetcher,
        topicName: String,
        partitions: Set<Int>,
        timestamp: Long
    ): ConsumeRecordsResponse = coroutineScope {
        val partitionDataList = partitions.map { partitionId ->
            async {
                fetcher.consumePartitionRecords(
                    topicName = topicName,
                    partitionId = partitionId,
                    timestamp = timestamp,
                    maxPollRecords = resolvedMaxPollRecords
                )
            }
        }.awaitAll()

        ConsumeRecordsResponse(
            clusterId = "",
            topicName = topicName,
            partitionDataList = partitionDataList
        )
    }

    /**
     * Build a subsequent consume request using tracked offsets.
     */
    @VisibleForTesting
    internal fun buildSubsequentConsumeRequest(): ConsumeRecordsRequest {
        return if (nextOffsets.isNotEmpty()) {
            ConsumeRecordsRequest(
                offsets = nextOffsets.map { (partitionId, offset) ->
                    PartitionOffset(partitionId = partitionId, offset = offset)
                },
                maxPollRecords = resolvedMaxPollRecords,
                fetchMaxBytes = resolvedFetchMaxBytes
            )
        } else {
            // No offsets tracked yet, fetch from end
            ConsumeRecordsRequest(
                fromBeginning = false,
                maxPollRecords = resolvedMaxPollRecords,
                fetchMaxBytes = resolvedFetchMaxBytes
            )
        }
    }

    /**
     * Update next offsets from response and sync partition map.
     * Removes stale partitions that are no longer in the response (handles partition removal).
     * When [partitionFilter] is set, only tracks offsets for partitions in the filter.
     */
    @VisibleForTesting
    internal fun updateNextOffsets(response: ConsumeRecordsResponse, partitionFilter: Set<Int>?) {
        val activePartitions = response.partitionDataList.map { it.partitionId }.toSet()

        // Remove partitions that are no longer in the response
        nextOffsets.keys.retainAll(activePartitions)

        // Update offsets for active partitions, respecting the partition filter
        response.partitionDataList.forEach { partitionData ->
            if (partitionFilter == null || partitionData.partitionId in partitionFilter) {
                nextOffsets[partitionData.partitionId] = partitionData.nextOffset
            }
        }
    }

    /**
     * Decode a raw base64 JSON element to bytes.
     * With `return_raw_base64_records=true`, all record values arrive as `{"__raw__": "<base64>"}`.
     */
    @VisibleForTesting
    internal fun decodeRawBytes(element: JsonElement?): ByteArray? {
        if (element == null || element is JsonNull) return null
        val rawElement = (element as? JsonObject)?.get("__raw__")
        if (rawElement == null || rawElement is JsonNull) return null
        val raw = rawElement.jsonPrimitive.content
        return Base64.getDecoder().decode(raw)
    }

    @VisibleForTesting
    internal suspend fun convertToConsumerRecord(
        record: PartitionConsumeRecord,
        topic: String,
        fetcher: DataPlaneFetcher
    ): ConsumerRecord<Any, Any> {
        // Decode headers FIRST, Cloud REST API returns header values as base64-encoded strings.
        // Must decode to byte[] (not UTF-8 string) so schema GUIDs in headers can be detected.
        val headers = RecordHeaders(
            record.headers.map { header ->
                val decodedValue = try {
                    Base64.getDecoder().decode(header.value)
                } catch (e: IllegalArgumentException) {
                    header.value.toByteArray()
                }
                RecordHeader(header.key, decodedValue)
            }
        )

        // Decode base64 once — all records use __raw__ format thanks to return_raw_base64_records=true
        val keyBytes = decodeRawBytes(record.key)
        val valueBytes = decodeRawBytes(record.value)
        val keySize = keyBytes?.size ?: 0
        val valueSize = valueBytes?.size ?: 0

        val key = extractValue(keyBytes, topic, fetcher, headers, isKey = true)
        val value = extractValue(valueBytes, topic, fetcher, headers, isKey = false)

        val timestampType = when (record.timestampType) {
            ApiTimestampType.NO_TIMESTAMP_TYPE -> TimestampType.NO_TIMESTAMP_TYPE
            ApiTimestampType.CREATE_TIME -> TimestampType.CREATE_TIME
            ApiTimestampType.LOG_APPEND_TIME -> TimestampType.LOG_APPEND_TIME
        }

        return ConsumerRecord(
            topic,
            record.partitionId,
            record.offset,
            record.timestamp,
            timestampType,
            keySize,
            valueSize,
            key,
            value,
            headers,
            null // leaderEpoch
        )
    }

    /**
     * Extract a typed value from raw bytes.
     * - SCHEMA_REGISTRY → full schema-aware deserialization (Avro/Protobuf/JSON Schema)
     * - Other types → convert raw bytes using the appropriate Kafka deserializer
     */
    @VisibleForTesting
    internal suspend fun extractValue(
        bytes: ByteArray?,
        topic: String,
        fetcher: DataPlaneFetcher,
        headers: RecordHeaders,
        isKey: Boolean
    ): Any? {
        if (bytes == null) return null

        val fieldType = (if (isKey) currentKeyConfig else currentValueConfig)?.type
            ?: KafkaFieldType.STRING

        if (fieldType == KafkaFieldType.SCHEMA_REGISTRY) {
            return deserializeSchemaEncoded(bytes, fetcher, headers, isKey)
        }

        return convertBytesToType(bytes, topic, fieldType)
    }

    /**
     * Create a Kafka deserializer for the given primitive field type.
     * Throws for schema types, those are handled by [deserializeSchemaEncoded].
     */
    @VisibleForTesting
    internal fun createDeserializer(type: KafkaFieldType): Deserializer<*> = when (type) {
        KafkaFieldType.STRING, KafkaFieldType.JSON -> StringDeserializer()
        KafkaFieldType.LONG -> LongDeserializer()
        KafkaFieldType.INTEGER -> IntegerDeserializer()
        KafkaFieldType.DOUBLE -> DoubleDeserializer()
        KafkaFieldType.FLOAT -> FloatDeserializer()
        KafkaFieldType.BASE64 -> ByteArrayDeserializer()
        KafkaFieldType.NULL -> VoidDeserializer()
        else -> throw IllegalArgumentException("Unsupported field type for byte deserialization: $type")
    }

    /**
     * Create a deserializer if the type is a primitive type, null for schema types.
     * Used in [start] to cache deserializers, schema types use [deserializeSchemaEncoded] instead.
     */
    @VisibleForTesting
    internal fun createDeserializerOrNull(type: KafkaFieldType): Deserializer<*>? = when (type) {
        KafkaFieldType.SCHEMA_REGISTRY, KafkaFieldType.PROTOBUF_CUSTOM, KafkaFieldType.AVRO_CUSTOM -> null
        else -> createDeserializer(type)
    }

    /**
     * Convert raw bytes to the selected type using the cached Kafka deserializer.
     * Should never be called with schema types, [extractValue] routes those to [deserializeSchemaEncoded].
     */
    @VisibleForTesting
    internal fun convertBytesToType(bytes: ByteArray, topic: String, type: KafkaFieldType): Any? {
        val deserializer = when (type) {
            currentKeyConfig?.type -> keyDeserializer
            currentValueConfig?.type -> valueDeserializer
            else -> null
        } ?: createDeserializer(type)

        return deserializer.deserialize(topic, bytes)
    }


    /**
     * Deserialize schema-encoded bytes using V1 (header GUID) or V0 (payload prefix) wire format.
     *
     * Priority order:
     * 1. V1: Schema GUID from `confluent.key.schemaId` / `confluent.value.schemaId` headers
     * 2. V0: Schema ID from payload prefix
     * 3. Fallback: return raw bytes as-is
     *
     */
    @VisibleForTesting
    internal suspend fun deserializeSchemaEncoded(
        bytes: ByteArray,
        fetcher: DataPlaneFetcher,
        headers: RecordHeaders,
        isKey: Boolean
    ): Any {
        // Priority 1: Schema GUID from headers (V1)
        val schemaGuid = getSchemaGuidFromHeaders(headers, isKey)

        // Priority 2: Schema ID from payload (V0)
        val schemaId = getSchemaIdFromRawBytes(bytes)

        if (schemaGuid == null && schemaId == null) return bytes

        val registryId = SchemaRegistryClusterId(
            clusterDataManager.getDataPlaneCache().getSchemaRegistryId()
                ?: throw IllegalStateException("Schema Registry ID is required for schema deserialization")
        )
        val cacheKey = if (schemaGuid != null) SchemaCacheKey.ByGuid(schemaGuid) else SchemaCacheKey.ById(schemaId!!)
        val parsedSchema = fetchAndParseSchema(registryId, cacheKey) {
            if (schemaGuid != null) {
                fetcher.getSchemaByGuid(schemaGuid.toString())
            } else {
                fetcher.getSchemaIdInfo(schemaId!!)
            }
        }

        // V0: strip 5-byte prefix (magic + schema ID). V1: payload has no prefix.
        val payloadBytes = if (schemaGuid != null) bytes else bytes.copyOfRange(5, bytes.size)
        return when (parsedSchema) {
            is AvroSchema -> deserializeAvro(payloadBytes, parsedSchema)
            is ProtobufSchema -> deserializeProtobuf(payloadBytes, parsedSchema)
            is JsonSchema -> String(payloadBytes, Charsets.UTF_8)
            else -> throw SerializationException("Unsupported schema type: ${parsedSchema.schemaType()}")
        }
    }

    private suspend fun fetchAndParseSchema(
        registryId: SchemaRegistryClusterId,
        key: SchemaCacheKey,
        fetch: suspend () -> io.confluent.intellijplugin.ccloud.model.response.SchemaByIdResponse
    ): ParsedSchema {
        val registryCache = schemaCache.getOrPut(registryId) { ConcurrentHashMap() }
        return registryCache.getOrPut(key) {
            val response = fetch()
            val schemaType = KafkaRegistryFormat.fromSchemaType(response.schemaType)
            KafkaRegistryUtil.parseSchema(schemaType, response.schema).getOrThrow()
        }
    }

    /**
     * Extract V0 schema ID from payload
     */
    @VisibleForTesting
    internal fun getSchemaIdFromRawBytes(rawBytes: ByteArray): Int? {
        if (rawBytes.size < 5 || rawBytes[0] != SchemaId.MAGIC_BYTE_V0) return null
        return ByteBuffer.wrap(rawBytes, 1, 4).getInt()
    }

    /**
     * Extract V1 schema GUID from Kafka headers
     */
    @VisibleForTesting
    internal fun getSchemaGuidFromHeaders(headers: RecordHeaders, isKey: Boolean): UUID? {
        val headerName = if (isKey) SchemaId.KEY_SCHEMA_ID_HEADER else SchemaId.VALUE_SCHEMA_ID_HEADER
        val header = headers.lastHeader(headerName) ?: return null
        val value = header.value() ?: return null
        if (value.size < 17 || value[0] != SchemaId.MAGIC_BYTE_V1) return null
        val buffer = ByteBuffer.wrap(value)
        buffer.get() // skip magic byte
        return UUID(buffer.getLong(), buffer.getLong())
    }

    /**
     * Deserialize Avro binary payload to a typed Avro object (e.g. GenericData.Record).
     * Returns the datum directly so the UI layer (KafkaEditorUtils.getValueAsString)
     */
    private fun deserializeAvro(payload: ByteArray, schema: AvroSchema): Any {
        val reader = GenericDatumReader<Any>(schema.rawSchema())
        val decoder = DecoderFactory.get().binaryDecoder(payload, null)
        return reader.read(null, decoder)
    }

    /**
     * Deserialize Protobuf binary payload (with varint message indexes) to a DynamicMessage.
     * Returns the message directly so the UI layer (KafkaEditorUtils.getValueAsString)
     */
    @VisibleForTesting
    internal fun deserializeProtobuf(payload: ByteArray, schema: ProtobufSchema): DynamicMessage {
        val buffer = ByteBuffer.wrap(payload)
        val indexes = MessageIndexes.readFrom(buffer)
        val messageName = schema.toMessageName(indexes)
        val descriptor = schema.toDescriptor(messageName)
            ?: schema.toDescriptor()
            ?: throw SerializationException(
                "No descriptor for ${schema.name()}"
            )
        // Read remaining bytes after message indexes
        val remaining = ByteArray(buffer.remaining())
        buffer[remaining]
        return DynamicMessage.parseFrom(descriptor, remaining)
    }

    override fun stop() {
        running.set(false)
        // Cancel the job and scope; the finally block in pollLoop handles onStop()
        pollingJob?.cancel()
        consumerScope?.cancel()
        consumerScope = null
        pollingJob = null
        currentKeyConfig = null
        currentValueConfig = null
        keyDeserializer = null
        valueDeserializer = null
        resolvedMaxPollRecords = null
        resolvedFetchMaxBytes = null
    }

    override fun isRunning(): Boolean = running.get()

    override fun dispose() {
        stop()
        nextOffsets.clear()
        schemaCache.clear()
    }

    companion object {
        /** Base delay for exponential backoff on errors. */
        private const val BASE_BACKOFF_MS = 1_000L

        /** Maximum delay for exponential backoff. */
        private const val MAX_BACKOFF_MS = 30_000L

        /** Delay when no records are available. */
        private const val EMPTY_POLL_DELAY_MS = 1_000L

        /** Delay when waiting for token refresh after 401. */
        private const val TOKEN_REFRESH_DELAY_MS = 5_000L

    }
}
