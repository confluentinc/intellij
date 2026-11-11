package io.confluent.intellijplugin.producer.client

import com.intellij.charts.dataframe.DataFrame
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudApiException
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.client.CCloudRetryPolicy
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordData
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordHeader
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordRequest
import io.confluent.intellijplugin.ccloud.model.response.SchemaVersionResponse
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordResponse
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.producer.models.AcksType
import io.confluent.intellijplugin.producer.models.Mode
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.producer.models.RecordCompression
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import com.google.protobuf.DynamicMessage
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.kafka.schemaregistry.protobuf.MessageIndexes
import io.confluent.kafka.serializers.schema.id.SchemaId
import io.confluent.intellijplugin.util.generator.FieldTemplateGenerator
import io.confluent.intellijplugin.util.generator.GenerateRandomData
import io.confluent.intellijplugin.util.csv.KafkaCsvUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.VisibleForTesting
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REST-based producer client for Confluent Cloud.
 *
 * Uses the Kafka REST API v3 produce endpoint with OAuth authentication.
 * Serializes key/value client-side and sends as BINARY (base64) or STRING data.
 */
class CCloudProducerClient(
    private val clusterDataManager: CCloudClusterDataManager,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    @VisibleForTesting internal val retryPolicy: CCloudRetryPolicy = CCloudRetryPolicy.DEFAULT,
) : ProducerClient {

    @VisibleForTesting
    internal val running = AtomicBoolean(false)
    private var produceJob: Job? = null

    override fun isRunning(): Boolean = running.get()

    override fun start(
        topic: String,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        recordCompression: RecordCompression,
        acks: AcksType,
        enableIdempotence: Boolean,
        forcePartition: Int,
        flowParams: ProducerFlowParams,
        onUpdate: (Long, List<KafkaRecord>) -> Unit
    ) {
        if (running.getAndSet(true)) {
            error("Producer is already running")
        }
        onStart()

        produceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                produceLoop(topic, key, value, headers, forcePartition, flowParams, onUpdate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().warn("CCloud producer error", e)
                RfsNotificationUtils.showExceptionMessage(
                    clusterDataManager.project,
                    e,
                    KafkaMessagesBundle.message("error.producer.title")
                )
            } finally {
                running.set(false)
                produceJob = null
                onStop()
            }
        }
    }

    private suspend fun produceLoop(
        topic: String,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        forcePartition: Int,
        flowParams: ProducerFlowParams,
        onUpdate: (Long, List<KafkaRecord>) -> Unit
    ) {
        val fetcher = clusterDataManager.getDataPlaneCache().getFetcher()
            ?: throw IllegalStateException("DataPlaneFetcher not initialized")

        val validatedPartition = validatePartition(forcePartition, topic, fetcher)

        val schemaCache = resolveSchemaCache(fetcher, key, value)

        val csvDataFrame = flowParams.csvFile?.let { KafkaCsvUtils.readDataFrame(it) }
        var produced = 0

        when (flowParams.mode) {
            Mode.MANUAL -> {
                produceBatch(
                    fetcher = fetcher,
                    topic = topic,
                    key = key,
                    value = value,
                    headers = headers,
                    forcePartition = validatedPartition,
                    flowParams = flowParams,
                    alreadyProducedCount = produced,
                    csvDataFrame = csvDataFrame,
                    schemaCache = schemaCache,
                    onUpdate = onUpdate
                )
            }

            Mode.AUTO -> {
                val startTime = System.currentTimeMillis()
                while (running.get()) {
                    if (flowParams.totalRequests != 0 && produced >= flowParams.totalRequests) break
                    if (flowParams.totalElapsedTime != 0 &&
                        (System.currentTimeMillis() - startTime) >= flowParams.totalElapsedTime
                    ) break

                    val keepGoing = produceBatch(
                        fetcher = fetcher,
                        topic = topic,
                        key = key,
                        value = value,
                        headers = headers,
                        forcePartition = validatedPartition,
                        flowParams = flowParams,
                        alreadyProducedCount = produced,
                        csvDataFrame = csvDataFrame,
                        schemaCache = schemaCache,
                        onUpdate = onUpdate
                    )
                    // Stopped by the user or by an exhausted produce; don't wait out the interval
                    if (!keepGoing || !running.get()) break
                    produced += flowParams.flowRecordsCountPerRequest
                    delay(flowParams.requestInterval.toLong())
                }
            }
        }
    }

    private suspend fun resolveSchemaCache(
        fetcher: DataPlaneFetcher,
        vararg fields: ConsumerProducerFieldConfig
    ): Map<String, SchemaVersionResponse> {
        val cache = ConcurrentHashMap<String, SchemaVersionResponse>()
        for (field in fields) {
            if (field.type == KafkaFieldType.SCHEMA_REGISTRY && field.schemaName.isNotEmpty()) {
                cache.getOrPut(field.schemaName) {
                    fetcher.getLatestVersionInfo(field.schemaName)
                }
            }
        }
        return cache
    }

    private suspend fun produceBatch(
        fetcher: DataPlaneFetcher,
        topic: String,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        forcePartition: Int,
        flowParams: ProducerFlowParams,
        alreadyProducedCount: Int,
        csvDataFrame: DataFrame?,
        schemaCache: Map<String, SchemaVersionResponse>,
        onUpdate: (Long, List<KafkaRecord>) -> Unit
    ): Boolean {
        for (i in 0 until flowParams.flowRecordsCountPerRequest) {
            if (!running.get()) break

            val recordKey = resolveFieldValue(key, flowParams.generateRandomKeys, csvDataFrame, alreadyProducedCount + i, isKey = true)
            val recordValue = resolveFieldValue(value, flowParams.generateRandomValues, csvDataFrame, alreadyProducedCount + i, isKey = false)

            val processedHeaders = headers.map {
                val headerKey = FieldTemplateGenerator.processTemplate(it.name ?: "")
                val headerValue = FieldTemplateGenerator.processTemplate(it.value ?: "")
                Property(headerKey, headerValue)
            }

            val request = buildProduceRequest(
                fetcher, recordKey, recordValue, processedHeaders, topic, forcePartition, schemaCache
            )

            val (response, duration) = try {
                produceWithRetry(fetcher, topic, request) ?: break
            } catch (_: CancellationException) {
                // Job cancelled while waiting (rate limit, 429 or retry backoff); no record sent, safe to stop
                break
            } catch (e: CCloudProduceGaveUpException) {
                // Show it in the results table and stop (including AUTO mode); other errors keep the balloon
                thisLogger().info("CCloud produce to $topic gave up: ${e.message}", e)
                onUpdate(0, listOf(createErrorRecord(recordKey, recordValue, topic, forcePartition.takeIf { it >= 0 }, e, duration = 0)))
                return false
            }

            val record = KafkaRecord(
                keyType = recordKey.type,
                valueType = recordValue.type,
                error = null,
                key = recordKey.getValueObj(),
                value = recordValue.getValueObj(),
                topic = response.topicName ?: topic,
                partition = response.partitionId ?: -1,
                offset = response.offset ?: -1,
                duration = duration,
                timestamp = response.timestamp?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis(),
                keySize = response.key?.size ?: 0,
                valueSize = response.value?.size ?: 0,
                headers = processedHeaders,
                keyFormat = recordKey.schemaFormat,
                valueFormat = recordValue.schemaFormat
            )
            onUpdate(record.duration, listOf(record))
        }
        return true
    }

    private fun resolveFieldValue(
        field: ConsumerProducerFieldConfig,
        generateRandom: Boolean,
        csvDataFrame: DataFrame?,
        recordIndex: Int,
        isKey: Boolean
    ): ConsumerProducerFieldConfig {
        val resolved = when {
            csvDataFrame != null -> field.copy(
                valueText = if (isKey) KafkaCsvUtils.getKey(csvDataFrame, recordIndex)
                else KafkaCsvUtils.getValue(csvDataFrame, recordIndex)
            )
            generateRandom -> field.copy(
                valueText = GenerateRandomData.generate(clusterDataManager.project, field)
            )
            else -> field
        }
        return resolved.copy(
            valueText = FieldTemplateGenerator.processTemplate(resolved.valueText)
        )
    }

    @VisibleForTesting
    internal suspend fun validatePartition(
        forcePartition: Int,
        topic: String,
        fetcher: DataPlaneFetcher
    ): Int {
        if (forcePartition < 0) return forcePartition

        val actualPartitions = fetcher.describeTopicPartitions(topic)
            .map { it.partitionId }.toSet()
        if (forcePartition !in actualPartitions) {
            error(KafkaMessagesBundle.message("producer.wrong.partition", forcePartition, topic))
        }
        return forcePartition
    }

    @VisibleForTesting
    internal suspend fun buildProduceRequest(
        fetcher: DataPlaneFetcher,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        topic: String,
        forcePartition: Int,
        schemaCache: Map<String, SchemaVersionResponse> = emptyMap()
    ): ProduceRecordRequest {
        return ProduceRecordRequest(
            partitionId = if (forcePartition >= 0) forcePartition else null,
            headers = headers.takeIf { it.isNotEmpty() }?.map { header ->
                ProduceRecordHeader(
                    name = header.name ?: "",
                    value = header.value?.let {
                        Base64.getEncoder().encodeToString(it.toByteArray())
                    }
                )
            },
            key = buildRecordData(fetcher, key, topic, schemaCache),
            value = buildRecordData(fetcher, value, topic, schemaCache),
            timestamp = null
        )
    }

    @VisibleForTesting
    internal suspend fun buildRecordData(
        fetcher: DataPlaneFetcher,
        field: ConsumerProducerFieldConfig,
        topic: String,
        schemaCache: Map<String, SchemaVersionResponse> = emptyMap()
    ): ProduceRecordData? {
        if (field.type == KafkaFieldType.NULL) return null

        return when (field.type) {
            KafkaFieldType.STRING -> ProduceRecordData(
                type = "STRING",
                data = field.valueText
            )
            // STRING avoids double-encoding; mirrors KafkaProducerClient which uses StringSerializer for JSON
            KafkaFieldType.JSON -> ProduceRecordData(
                type = "STRING",
                data = field.valueText
            )
            KafkaFieldType.SCHEMA_REGISTRY -> buildSchemaRegistryData(fetcher, field, schemaCache)
            KafkaFieldType.AVRO_CUSTOM -> buildAvroData(field)
            KafkaFieldType.PROTOBUF_CUSTOM -> buildProtobufData(field)
            else -> {
                // Primitive types (LONG, INTEGER, DOUBLE, FLOAT, BASE64)
                val valueObj = field.getValueObj() ?: return null
                val bytes = serializePrimitive(field.type, topic, valueObj)
                ProduceRecordData(
                    type = "BINARY",
                    data = Base64.getEncoder().encodeToString(bytes)
                )
            }
        }
    }

    /**
     * Build record data for SCHEMA_REGISTRY types.
     *
     * Serialize client-side and prepend the Confluent V0 wire format prefix.
     * The schema ID is looked up from the pre-resolved cache, falling back to a
     * network call only if the subject is not cached.
     */
    private suspend fun buildSchemaRegistryData(
        fetcher: DataPlaneFetcher,
        field: ConsumerProducerFieldConfig,
        schemaCache: Map<String, SchemaVersionResponse> = emptyMap()
    ): ProduceRecordData {
        val schemaInfo = schemaCache[field.schemaName]
            ?: fetcher.getLatestVersionInfo(field.schemaName)
        val schemaId = schemaInfo.id

        val payloadBytes = when (field.schemaFormat) {
            KafkaRegistryFormat.AVRO -> {
                val avroSchema = field.parsedSchema as? AvroSchema
                    ?: error("Expected AvroSchema for format ${field.schemaFormat}, got ${field.parsedSchema?.javaClass?.simpleName}")
                val record = AvroSchemaUtils.toObject(field.valueText, avroSchema)
                serializeAvro(record, avroSchema)
            }
            KafkaRegistryFormat.PROTOBUF -> {
                val protobufSchema = field.parsedSchema as? ProtobufSchema
                    ?: error("Expected ProtobufSchema for format ${field.schemaFormat}, got ${field.parsedSchema?.javaClass?.simpleName}")
                val message = ProtobufSchemaUtils.toObject(field.valueText, protobufSchema) as DynamicMessage
                serializeProtobuf(message)
            }
            KafkaRegistryFormat.JSON -> field.valueText.toByteArray(Charsets.UTF_8)
            KafkaRegistryFormat.UNKNOWN -> error("Schema format unknown")
        }

        val wireBytes = prependSchemaIdPrefix(schemaId, payloadBytes)
        return ProduceRecordData(
            type = "BINARY",
            data = Base64.getEncoder().encodeToString(wireBytes)
        )
    }

    /**
     * Serialize Avro data to binary bytes client-side (no wire format prefix).
     * Used for AVRO_CUSTOM where there is no SR subject.
     */
    private fun buildAvroData(field: ConsumerProducerFieldConfig): ProduceRecordData {
        val avroSchema = field.parsedSchema as AvroSchema
        val record = AvroSchemaUtils.toObject(field.valueText, avroSchema)
        val bytes = serializeAvro(record, avroSchema)
        return ProduceRecordData(
            type = "BINARY",
            data = Base64.getEncoder().encodeToString(bytes)
        )
    }

    /**
     * Serialize Protobuf data to binary bytes client-side (no wire format prefix).
     * Used for PROTOBUF_CUSTOM where there is no SR subject.
     */
    private fun buildProtobufData(field: ConsumerProducerFieldConfig): ProduceRecordData {
        val protobufSchema = field.parsedSchema as ProtobufSchema
        val message = ProtobufSchemaUtils.toObject(field.valueText, protobufSchema) as DynamicMessage
        return ProduceRecordData(
            type = "BINARY",
            data = Base64.getEncoder().encodeToString(message.toByteArray())
        )
    }

    /**
     * Prepend the Confluent V0 wire format prefix to payload bytes.
     * Mirror of [CCloudConsumerClient.getSchemaIdFromRawBytes] which strips this prefix.
     */
    @VisibleForTesting
    internal fun prependSchemaIdPrefix(schemaId: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 4 + payload.size)
        buffer.put(SchemaId.MAGIC_BYTE_V0)
        buffer.putInt(schemaId)
        buffer.put(payload)
        return buffer.array()
    }

    /**
     * Serialize a Protobuf message to bytes with message index varints.
     * Mirror of [CCloudConsumerClient.deserializeProtobuf] which reads these indexes.
     */
    @VisibleForTesting
    internal fun serializeProtobuf(message: DynamicMessage): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        // Write default message indexes (single message at index 0)
        outputStream.write(MessageIndexes(listOf(0)).toByteArray())
        message.writeTo(outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Serialize an Avro value to binary bytes.
     * Mirror of [CCloudConsumerClient.deserializeAvro].
     */
    @VisibleForTesting
    internal fun serializeAvro(value: Any, schema: AvroSchema): ByteArray {
        val avroSchema = schema.rawSchema()
        val writer = GenericDatumWriter<Any>(avroSchema)
        val outputStream = java.io.ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(outputStream, null)
        writer.write(value, encoder)
        encoder.flush()
        return outputStream.toByteArray()
    }

    /**
     * Produce a single record, retrying transient errors per [retryPolicy].
     * Returns null if the producer is stopped, allowing the caller to report partial results.
     *
     * - Error code in a 200 response body ([isRetryableBodyError]): retried with policy backoff.
     * - HTTP 429: not retried here, because the REST client already retried it (honoring
     *   `Retry-After`). Throws [CCloudProduceRetriesExhaustedException] with `rateLimited = true`.
     * - HTTP 502/503/504 ([isRetryableHttpError]): retried with policy backoff, never shorter than `Retry-After`.
     * - HTTP 429 or 5xx with a `Retry-After` above [CCloudRetryPolicy.maxRetryAfter]: not retried,
     *   since retrying sooner than the server asked defeats `Retry-After` (as in [CCloudRestClient]).
     *   Throws [CCloudProduceRetryAfterTooLongException] carrying the server's value.
     * - Anything else, including connect failures and timeouts (status 0): rethrown unchanged.
     *
     * @throws CCloudProduceRetriesExhaustedException when every attempt failed with a retryable error
     * @throws CCloudProduceRetryAfterTooLongException when the server asked to wait too long to retry
     */
    @VisibleForTesting
    internal suspend fun produceWithRetry(
        fetcher: DataPlaneFetcher,
        topic: String,
        request: ProduceRecordRequest
    ): Pair<ProduceRecordResponse, Long>? {
        val maxAttempts = retryPolicy.maxAttempts
        var retryIndex = 0

        while (true) {
            // Stopped — return null so the batch loop reports partial results
            if (!running.get()) return null

            val startTime = System.currentTimeMillis()
            val failure = try {
                val response = fetcher.produceRecord(topic, request)
                val errorCode = response.errorCode
                if (errorCode == null || errorCode == 200) {
                    return Pair(response, System.currentTimeMillis() - startTime)
                }
                // Application-level error in a 200 response body; classified below, outside this try
                CCloudApiException(response.message ?: "Produce failed with error code $errorCode", errorCode)
            } catch (e: CCloudApiException) {
                // HTTP-level errors thrown by the REST client
                if (retryPolicy.exceedsMaxRetryAfter(e.retryAfter)) {
                    thisLogger().debug("Produce Retry-After ${e.retryAfter} exceeds ${retryPolicy.maxRetryAfter}, not retrying")
                    throw CCloudProduceRetryAfterTooLongException(e, checkNotNull(e.retryAfter))
                }
                if (e.statusCode == 429) {
                    thisLogger().debug("Produce rate limited after REST client retries: ${e.message}")
                    throw CCloudProduceRetriesExhaustedException(retryIndex + 1, e, rateLimited = true)
                }
                if (!isRetryableHttpError(e.statusCode)) {
                    thisLogger().debug("Non-retryable HTTP error during produce: ${e.statusCode} ${e.message}")
                    throw e
                }
                e
            }

            // HTTP failures that reach here are 502/503/504, which are also retryable body codes
            if (!isRetryableBodyError(failure.statusCode)) {
                thisLogger().debug("Non-retryable produce error: ${failure.statusCode} ${failure.message}")
                throw failure
            }
            if (retryIndex == maxAttempts - 1) {
                // HTTP 429 throws above, so a 429 here came from the response body
                throw CCloudProduceRetriesExhaustedException(maxAttempts, failure, rateLimited = failure.statusCode == 429)
            }

            val retryDelay = retryPolicy.delayFor(retryIndex, failure.retryAfter)
            thisLogger().debug(
                "Retryable produce error (attempt ${retryIndex + 1}/$maxAttempts): " +
                    "${failure.statusCode}, retrying in $retryDelay"
            )
            delay(retryDelay)
            retryIndex++
        }
    }

    /**
     * Error codes in a 200 produce response body that are worth retrying: 429 and the REST client's
     * transient server statuses. Other 5xx (500, 501, 505, ...) won't succeed on a retry.
     */
    @VisibleForTesting
    internal fun isRetryableBodyError(errorCode: Int): Boolean =
        errorCode == 429 || errorCode in CCloudRestClient.TRANSIENT_SERVER_STATUSES

    /**
     * HTTP statuses the producer retries itself: the REST client's transient server statuses
     * (502/503/504), which it doesn't retry for a non-idempotent POST. 429 is excluded because the
     * REST client already retries it; status 0 (connect failure / timeout) is excluded because a
     * produce is not idempotent; other 5xx won't succeed on a retry.
     */
    @VisibleForTesting
    internal fun isRetryableHttpError(statusCode: Int): Boolean =
        statusCode in CCloudRestClient.TRANSIENT_SERVER_STATUSES

    /**
     * Serialize a primitive value to bytes using the appropriate Kafka serializer.
     */
    @VisibleForTesting
    internal fun serializePrimitive(type: KafkaFieldType, topic: String, value: Any): ByteArray {
        val serializer: Serializer<*> = when (type) {
            KafkaFieldType.LONG -> LongSerializer()
            KafkaFieldType.INTEGER -> IntegerSerializer()
            KafkaFieldType.DOUBLE -> DoubleSerializer()
            KafkaFieldType.FLOAT -> FloatSerializer()
            KafkaFieldType.BASE64 -> ByteArraySerializer()
            else -> StringSerializer()
        }
        @Suppress("UNCHECKED_CAST")
        return (serializer as Serializer<Any>).serialize(topic, value)
    }

    override fun stop() {
        running.set(false)
        produceJob?.cancel()
    }

    override fun dispose() {
        stop()
    }
}

/** The producer gave up on a record; shown as an error row in the results table. */
sealed class CCloudProduceGaveUpException(
    message: String,
    override val cause: CCloudApiException
) : Exception(message, cause)

/**
 * A produce request failed with a transient error and the producer gave up retrying it.
 *
 * @param attempts Produce attempts made by [CCloudProducerClient] (the REST client may have made
 *   more HTTP calls underneath, e.g. when it retried a 429)
 * @param cause The last failure
 * @param rateLimited True when Confluent Cloud kept rejecting the request with HTTP 429
 */
class CCloudProduceRetriesExhaustedException(
    val attempts: Int,
    cause: CCloudApiException,
    val rateLimited: Boolean = false
) : CCloudProduceGaveUpException(
    if (rateLimited) KafkaMessagesBundle.message("producer.ccloud.rate.limited", cause.message.orEmpty())
    else KafkaMessagesBundle.message("producer.ccloud.retries.exhausted", attempts, cause.message.orEmpty()),
    cause
)

/**
 * Confluent Cloud asked to wait longer than [CCloudRetryPolicy.maxRetryAfter] before retrying,
 * so the producer stopped instead of retrying sooner than asked.
 *
 * @param retryAfter The server's `Retry-After`, unclamped
 */
class CCloudProduceRetryAfterTooLongException(
    cause: CCloudApiException,
    val retryAfter: Duration
) : CCloudProduceGaveUpException(
    KafkaMessagesBundle.message("producer.ccloud.retry.after.too.long", cause.statusCode, retryAfter, cause.message.orEmpty()),
    cause
)
