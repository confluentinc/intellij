package io.confluent.intellijplugin.producer.client

import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudApiException
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordData
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordHeader
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordRequest
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
import io.confluent.intellijplugin.util.generator.FieldTemplateGenerator
import io.confluent.intellijplugin.util.generator.GenerateRandomData
import io.confluent.intellijplugin.util.csv.KafkaCsvUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.VisibleForTesting
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

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
) : ProducerClient {

    private val running = AtomicBoolean(false)
    private var produceJob: Job? = null
    private var producerScope: CoroutineScope? = null

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

        producerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        produceJob = producerScope!!.launch {
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

        val csvDataFrame = flowParams.csvFile?.let { KafkaCsvUtils.readDataFrame(it) }
        var produced = 0

        when (flowParams.mode) {
            Mode.MANUAL -> {
                val records = produceBatch(
                    fetcher = fetcher,
                    topic = topic,
                    key = key,
                    value = value,
                    headers = headers,
                    forcePartition = forcePartition,
                    flowParams = flowParams,
                    alreadyProducedCount = produced,
                    csvDataFrame = csvDataFrame
                )
                if (records.isNotEmpty()) {
                    onUpdate(records.sumOf { it.duration }, records)
                }
            }

            Mode.AUTO -> {
                val startTime = System.currentTimeMillis()
                while (running.get() && (producerScope?.isActive == true)) {
                    if (flowParams.totalRequests != 0 && produced >= flowParams.totalRequests) break
                    if (flowParams.totalElapsedTime != 0 &&
                        (System.currentTimeMillis() - startTime) >= flowParams.totalElapsedTime
                    ) break

                    val records = produceBatch(
                        fetcher = fetcher,
                        topic = topic,
                        key = key,
                        value = value,
                        headers = headers,
                        forcePartition = forcePartition,
                        flowParams = flowParams,
                        alreadyProducedCount = produced,
                        csvDataFrame = csvDataFrame
                    )
                    if (records.isNotEmpty()) {
                        onUpdate(records.sumOf { it.duration }, records)
                    }
                    produced += flowParams.flowRecordsCountPerRequest
                    delay(flowParams.requestInterval.toLong())
                }
            }
        }
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
        csvDataFrame: com.intellij.charts.dataframe.DataFrame?
    ): List<KafkaRecord> {
        val records = mutableListOf<KafkaRecord>()
        repeat(flowParams.flowRecordsCountPerRequest) { i ->
            if (!running.get()) return records

            val recordKey = resolveFieldValue(key, flowParams.generateRandomKeys, csvDataFrame, alreadyProducedCount + i, isKey = true)
            val recordValue = resolveFieldValue(value, flowParams.generateRandomValues, csvDataFrame, alreadyProducedCount + i, isKey = false)

            val processedHeaders = headers.map {
                val headerKey = FieldTemplateGenerator.processTemplate(it.name ?: "")
                val headerValue = FieldTemplateGenerator.processTemplate(it.value ?: "")
                Property(headerKey, headerValue)
            }

            val request = buildProduceRequest(
                fetcher, recordKey, recordValue, processedHeaders, topic, forcePartition
            )

            val (response, duration) = produceWithRetry(fetcher, topic, request)

            records.add(
                KafkaRecord(
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
                    valueFormat = recordValue.schemaFormat,
                    errror = null
                )
            )
        }
        return records
    }

    private fun resolveFieldValue(
        field: ConsumerProducerFieldConfig,
        generateRandom: Boolean,
        csvDataFrame: com.intellij.charts.dataframe.DataFrame?,
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
    internal suspend fun buildProduceRequest(
        fetcher: DataPlaneFetcher,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        topic: String,
        forcePartition: Int
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
            key = buildRecordData(fetcher, key, topic),
            value = buildRecordData(fetcher, value, topic),
            timestamp = null
        )
    }

    @VisibleForTesting
    internal suspend fun buildRecordData(
        fetcher: DataPlaneFetcher,
        field: ConsumerProducerFieldConfig,
        topic: String
    ): ProduceRecordData? {
        if (field.type == KafkaFieldType.NULL) return null

        return when (field.type) {
            KafkaFieldType.STRING -> ProduceRecordData(
                type = "STRING",
                data = field.valueText
            )
            KafkaFieldType.JSON -> ProduceRecordData(
                type = "JSON",
                data = field.valueText
            )
            KafkaFieldType.SCHEMA_REGISTRY -> buildSchemaRegistryData(fetcher, field)
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
     * Serialize client-side and prepend the Confluent V0 wire format prefix
     * The schema ID is resolved by fetching the latest version from Schema Registry.
     */
    private suspend fun buildSchemaRegistryData(
        fetcher: DataPlaneFetcher,
        field: ConsumerProducerFieldConfig
    ): ProduceRecordData {
        val schemaInfo = fetcher.getLatestVersionInfo(field.schemaName)
        val schemaId = schemaInfo.id

        val payloadBytes = when (field.schemaFormat) {
            KafkaRegistryFormat.AVRO -> {
                val avroSchema = field.parsedSchema as AvroSchema
                val record = AvroSchemaUtils.toObject(field.valueText, avroSchema)
                serializeAvro(record, avroSchema)
            }
            KafkaRegistryFormat.PROTOBUF -> {
                val protobufSchema = field.parsedSchema as ProtobufSchema
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
     * Prepend the Confluent V0 wire format prefix to payload bytes.*
     * Mirror of [CCloudConsumerClient.getSchemaIdFromRawBytes] which strips this prefix.
     */
    @VisibleForTesting
    internal fun prependSchemaIdPrefix(schemaId: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 4 + payload.size)
        buffer.put(0x00.toByte()) // magic byte
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
     * Produce a single record with retry and exponential backoff for transient errors.
     * Retries on 429 (rate limit) and 5xx (server errors). Fails immediately on 4xx client errors.
     */
    private suspend fun produceWithRetry(
        fetcher: DataPlaneFetcher,
        topic: String,
        request: ProduceRecordRequest
    ): Pair<ProduceRecordResponse, Long> {
        var lastException: CCloudApiException? = null

        repeat(MAX_RETRIES) { attempt ->
            if (!running.get()) throw CancellationException("Producer stopped")

            val startTime = System.currentTimeMillis()
            val response = fetcher.produceRecord(topic, request)
            val duration = System.currentTimeMillis() - startTime

            if (response.errorCode == null || response.errorCode == 200) {
                return Pair(response, duration)
            }

            val exception = CCloudApiException(
                response.message ?: "Produce failed with error code ${response.errorCode}",
                response.errorCode
            )

            if (!isRetryableStatus(response.errorCode)) {
                throw exception
            }

            lastException = exception
            thisLogger().debug("Retryable produce error (attempt ${attempt + 1}/$MAX_RETRIES): ${response.errorCode}")

            val backoffMs = min(
                BASE_BACKOFF_MS * (1L shl min(attempt, 5)),
                MAX_BACKOFF_MS
            )
            delay(backoffMs)
        }

        throw lastException ?: CCloudApiException("Produce failed after $MAX_RETRIES retries", 0)
    }

    @VisibleForTesting
    internal fun isRetryableStatus(statusCode: Int): Boolean =
        statusCode == 429 || statusCode in 500..599

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
        producerScope?.cancel()
        producerScope = null
        produceJob = null
    }

    override fun dispose() {
        stop()
    }

    companion object {
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val MAX_RETRIES = 5
    }
}
