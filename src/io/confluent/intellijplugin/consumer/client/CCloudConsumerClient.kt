package io.confluent.intellijplugin.consumer.client

import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsRequest
import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsResponse
import io.confluent.intellijplugin.ccloud.model.response.PartitionConsumeRecord
import io.confluent.intellijplugin.ccloud.model.response.PartitionOffset
import io.confluent.intellijplugin.ccloud.model.response.TimestampType as ApiTimestampType
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.data.ClusterScopedDataManager
import io.confluent.intellijplugin.util.KafkaOffsetUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * REST-based consumer client for Confluent Cloud.
 *
 * Uses Confluent Cloud internal REST API with OAuth authentication
 * to consume records from Kafka topics.
 *
 * @param clusterDataManager The cluster-scoped data manager for CCloud connection
 * @param onStart Callback invoked when consumption starts
 * @param onStop Callback invoked when consumption stops
 */
class CCloudConsumerClient(
    private val clusterDataManager: ClusterScopedDataManager,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
) : ConsumerClient {

    private val running = AtomicBoolean(false)
    private var pollingJob: Job? = null

    // Use our own independent scope to prevent cancellation from UI operations
    // SupervisorJob ensures child coroutine failures don't cancel the scope
    private var consumerScope: CoroutineScope? = null

    // Track next offsets per partition for subsequent requests
    private val nextOffsets = mutableMapOf<Int, Long>()

    override fun start(
        config: StorageConsumerConfig,
        valueConfig: ConsumerProducerFieldConfig,
        keyConfig: ConsumerProducerFieldConfig,
        consume: (Long, List<ConsumerRecord<Any, Any>>) -> Unit,
        timestampUpdate: () -> Unit,
        consumeError: (Throwable, Int?, Long?) -> Unit
    ) {
        running.set(true)
        onStart()
        nextOffsets.clear()

        // Create a new independent scope for this consumption session
        // Using Dispatchers.IO for network operations
        consumerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        pollingJob = consumerScope!!.launch {
            try {
                pollLoop(config, consume, timestampUpdate, consumeError)
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
        consume: (Long, List<ConsumerRecord<Any, Any>>) -> Unit,
        timestampUpdate: () -> Unit,
        consumeError: (Throwable, Int?, Long?) -> Unit
    ) {
        val fetcher = clusterDataManager.getDataPlaneCache().getFetcher()
            ?: throw IllegalStateException("DataPlaneFetcher not initialized")

        val topicName = config.getInnerTopic()
        val limit = config.getLimit()
        var recordsConsumed = 0L
        var isFirstRequest = true
        var consecutiveErrors = 0

        while (running.get() && (consumerScope?.isActive == true)) {
            val startTime = System.currentTimeMillis()

            try {
                val request = if (isFirstRequest) {
                    buildInitialConsumeRequest(config, fetcher)
                } else {
                    buildSubsequentConsumeRequest()
                }
                isFirstRequest = false

                val response = fetcher.consumeRecords(topicName, request)
                val pollTime = System.currentTimeMillis() - startTime

                // Reset error counter on successful request
                consecutiveErrors = 0

                timestampUpdate()

                // Update next offsets from response
                updateNextOffsets(response)

                // Flatten all records from all partitions
                val allRecords = response.partitionDataList.flatMap { partitionData ->
                    partitionData.records.map { record ->
                        convertToConsumerRecord(record, topicName)
                    }
                }

                if (allRecords.isEmpty()) {
                    // No new records, back off before next poll
                    delay(EMPTY_POLL_DELAY_MS)
                    continue
                }

                // Apply filters (client-side)
                val filteredRecords = applyFilters(allRecords, config)

                if (filteredRecords.isNotEmpty()) {
                    consume(pollTime, filteredRecords)
                    recordsConsumed += filteredRecords.size
                }

                // Check topic record count limit
                if (limit.topicRecordsCount != null && recordsConsumed >= limit.topicRecordsCount) {
                    break
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
                consecutiveErrors++

                // Check for 401 Unauthorized (token expiration)
                val is401 = e.message?.contains("401") == true ||
                    e.cause?.message?.contains("401") == true

                if (is401) {
                    // Token may have expired - wait for token refresh service
                    consumeError(e, null, null)
                    delay(TOKEN_REFRESH_DELAY_MS)
                } else {
                    // Apply exponential backoff for other errors
                    val backoffMs = min(
                        BASE_BACKOFF_MS * (1L shl min(consecutiveErrors, 5)),
                        MAX_BACKOFF_MS
                    )
                    consumeError(e, null, null)
                    delay(backoffMs)
                }
            }
        }
    }

    /**
     * Build the initial consume request based on the start position type.
     * This method may make additional API calls to resolve partition offsets.
     */
    private suspend fun buildInitialConsumeRequest(
        config: StorageConsumerConfig,
        fetcher: DataPlaneFetcher
    ): ConsumeRecordsRequest {
        val startsWith = config.getStartsWith()
        val topicName = config.getInnerTopic()

        return when (startsWith.type) {
            ConsumerStartType.THE_BEGINNING -> ConsumeRecordsRequest(
                fromBeginning = true,
                maxPollRecords = 100
            )

            ConsumerStartType.NOW -> ConsumeRecordsRequest(
                fromBeginning = false,
                maxPollRecords = 100
            )

            ConsumerStartType.OFFSET -> {
                // Offset is relative to beginning offset (user enters 10 → start from beginningOffset + 10)
                val offset = startsWith.offset ?: 0L
                val offsetInfo = fetcher.getTopicPartitionOffsets(topicName)
                ConsumeRecordsRequest(
                    offsets = offsetInfo.map { (partitionId, info) ->
                        PartitionOffset(partitionId, info.beginningOffset + offset)
                    },
                    maxPollRecords = 100
                )
            }

            ConsumerStartType.LATEST_OFFSET_MINUS_X -> {
                // Offset is already negative from ConsumerEditorUtils (user enters 10 → offset = -10)
                // So endOffset + offset = endOffset + (-10) = endOffset - 10
                val offset = startsWith.offset ?: 0L
                val offsetInfo = fetcher.getTopicPartitionOffsets(topicName)
                ConsumeRecordsRequest(
                    offsets = offsetInfo.map { (partitionId, info) ->
                        PartitionOffset(partitionId, max(0, info.endOffset + offset))
                    },
                    maxPollRecords = 100
                )
            }

            ConsumerStartType.SPECIFIC_DATE,
            ConsumerStartType.LAST_HOUR,
            ConsumerStartType.TODAY,
            ConsumerStartType.YESTERDAY -> {
                // Use KafkaOffsetUtils to calculate timestamp for LAST_HOUR, TODAY, YESTERDAY
                // For SPECIFIC_DATE, it returns startsWith.time directly
                val timestamp = KafkaOffsetUtils.calculateStartTime(startsWith)
                ConsumeRecordsRequest(
                    timestamp = timestamp,
                    maxPollRecords = 100
                )
            }

            ConsumerStartType.CONSUMER_GROUP -> {
                // Consumer groups not supported by CCloud REST API - fall back to NOW
                ConsumeRecordsRequest(
                    fromBeginning = false,
                    maxPollRecords = 100
                )
            }
        }
    }

    /**
     * Build a subsequent consume request using tracked offsets.
     */
    private fun buildSubsequentConsumeRequest(): ConsumeRecordsRequest {
        return if (nextOffsets.isNotEmpty()) {
            ConsumeRecordsRequest(
                offsets = nextOffsets.map { (partitionId, offset) ->
                    PartitionOffset(partitionId = partitionId, offset = offset)
                },
                maxPollRecords = 100
            )
        } else {
            // No offsets tracked yet, fetch from end
            ConsumeRecordsRequest(
                fromBeginning = false,
                maxPollRecords = 100
            )
        }
    }

    /**
     * Update next offsets from response and sync partition map.
     * Removes stale partitions that are no longer in the response (handles partition removal).
     */
    private fun updateNextOffsets(response: ConsumeRecordsResponse) {
        val activePartitions = response.partitionDataList.map { it.partitionId }.toSet()

        // Remove partitions that are no longer in the response
        nextOffsets.keys.retainAll(activePartitions)

        // Update offsets for active partitions
        response.partitionDataList.forEach { partitionData ->
            nextOffsets[partitionData.partitionId] = partitionData.nextOffset
        }
    }

    private fun convertToConsumerRecord(
        record: PartitionConsumeRecord,
        topic: String
    ): ConsumerRecord<Any, Any> {
        val key = extractValue(record.key)
        val value = extractValue(record.value)

        val headers = RecordHeaders(
            record.headers.map { header ->
                RecordHeader(header.key, header.value.toByteArray())
            }
        )

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
            -1, // serializedKeySize - not available from REST
            -1, // serializedValueSize - not available from REST
            key,
            value,
            headers,
            null // leaderEpoch
        )
    }

    /**
     * Extract value from JSON element.
     * Handles both plain values and schema-encoded values ({"__raw__": "base64"}).
     */
    private fun extractValue(element: JsonElement?): Any? {
        if (element == null || element is JsonNull) {
            return null
        }

        // Check if it's a schema-encoded value
        if (element is JsonObject && element.containsKey("__raw__")) {
            val rawValue = element["__raw__"]?.jsonPrimitive?.content
            return if (rawValue != null) {
                try {
                    Base64.getDecoder().decode(rawValue)
                } catch (e: Exception) {
                    rawValue
                }
            } else {
                null
            }
        }

        // Plain JSON value - convert to string representation
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) element.content
                else element.content
            }
            else -> element.toString()
        }
    }

    private fun applyFilters(
        records: List<ConsumerRecord<Any, Any>>,
        config: StorageConsumerConfig
    ): List<ConsumerRecord<Any, Any>> {
        val filter = config.getFilter()
        return records.filter { record ->
            filter.isRecordPassFilter(record)
        }
    }

    override fun stop() {
        running.set(false)
        pollingJob?.let { job ->
            job.cancel()
            // Wait for graceful shutdown with timeout
            runBlocking {
                withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) {
                    job.join()
                }
            }
        }
        // Cancel the consumer scope to clean up resources
        consumerScope?.cancel()
        consumerScope = null
        pollingJob = null
        onStop()
    }

    override fun isRunning(): Boolean = running.get()

    override fun dispose() {
        stop()
        nextOffsets.clear()
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

        /** Timeout for graceful shutdown. */
        private const val SHUTDOWN_TIMEOUT_MS = 5_000L
    }
}
