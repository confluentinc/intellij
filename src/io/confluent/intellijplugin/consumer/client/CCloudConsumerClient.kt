package io.confluent.intellijplugin.consumer.client

import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsRequest
import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsResponse
import io.confluent.intellijplugin.ccloud.model.response.PartitionConsumeRecord
import io.confluent.intellijplugin.ccloud.model.response.PartitionOffset
import io.confluent.intellijplugin.ccloud.model.response.TimestampType as ApiTimestampType
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.data.ClusterScopedDataManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

/**
 * REST-based consumer client for Confluent Cloud.
 *
 * Uses Confluent Cloud internal REST API with OAuth authentication
 * to consume records from Kafka topics.
 *
 * @param clusterDataManager The cluster-scoped data manager for CCloud connection
 * @param onStart Callback invoked when consumption starts
 * @param onStop Callback invoked when consumption stops
 * @param scope CoroutineScope for the polling loop
 */
class CCloudConsumerClient(
    private val clusterDataManager: ClusterScopedDataManager,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    private val scope: CoroutineScope
) : ConsumerClient {

    private val running = AtomicBoolean(false)
    private var pollingJob: Job? = null

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

        pollingJob = scope.launch {
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

        while (running.get() && scope.isActive) {
            val startTime = System.currentTimeMillis()

            try {
                val request = buildConsumeRequest(config, isFirstRequest)
                isFirstRequest = false

                val response = fetcher.consumeRecords(topicName, request)
                val pollTime = System.currentTimeMillis() - startTime

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
                    delay(1000)
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
                consumeError(e, null, null)
                // Continue polling after error with backoff
                delay(2000)
            }
        }
    }

    private fun buildConsumeRequest(config: StorageConsumerConfig, isFirstRequest: Boolean): ConsumeRecordsRequest {
        val startsWith = config.getStartsWith()

        return if (isFirstRequest) {
            // First request: determine starting position
            when (startsWith.type) {
                ConsumerStartType.THE_BEGINNING -> ConsumeRecordsRequest(
                    fromBeginning = true,
                    maxPollRecords = 100
                )
                ConsumerStartType.NOW -> ConsumeRecordsRequest(
                    fromBeginning = false,
                    maxPollRecords = 100
                )
                ConsumerStartType.OFFSET, ConsumerStartType.LATEST_OFFSET_MINUS_X -> {
                    // If specific offset is provided, we need partition info
                    // For now, start from end and let subsequent requests use offset tracking
                    ConsumeRecordsRequest(
                        fromBeginning = false,
                        maxPollRecords = 100
                    )
                }
                else -> ConsumeRecordsRequest(
                    fromBeginning = false,
                    maxPollRecords = 100
                )
            }
        } else {
            // Subsequent requests: use tracked offsets
            if (nextOffsets.isNotEmpty()) {
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
    }

    private fun updateNextOffsets(response: ConsumeRecordsResponse) {
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
        pollingJob?.cancel()
        onStop()
    }

    override fun isRunning(): Boolean = running.get()

    override fun dispose() {
        stop()
        nextOffsets.clear()
    }
}
