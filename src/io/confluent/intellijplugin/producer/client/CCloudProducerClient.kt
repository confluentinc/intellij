package io.confluent.intellijplugin.producer.client

import com.intellij.charts.dataframe.DataFrame
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudApiException
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordData
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordHeader
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordRequest
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.producer.models.AcksType
import io.confluent.intellijplugin.producer.models.Mode
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.producer.models.RecordCompression
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
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
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.VisibleForTesting
import java.time.Instant
import java.util.Base64
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

        val validatedPartition = validatePartition(forcePartition, topic, fetcher)

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
                    forcePartition = validatedPartition,
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
                        forcePartition = validatedPartition,
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
        csvDataFrame: DataFrame?
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
                recordKey, recordValue, processedHeaders, topic, forcePartition
            )

            val startTime = System.currentTimeMillis()
            val response = fetcher.produceRecord(topic, request)
            val duration = System.currentTimeMillis() - startTime

            if (response.errorCode != null && response.errorCode != 200) {
                throw CCloudApiException(
                    response.message ?: "Produce failed with error code ${response.errorCode}",
                    response.errorCode
                )
            }

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
                    valueFormat = recordValue.schemaFormat
                )
            )
        }
        return records
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
    internal fun buildProduceRequest(
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
            key = buildRecordData(key, topic),
            value = buildRecordData(value, topic),
            timestamp = null
        )
    }

    @VisibleForTesting
    internal fun buildRecordData(
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

}
