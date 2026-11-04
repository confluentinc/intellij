package io.confluent.intellijplugin.producer.client

import com.intellij.charts.dataframe.DataFrame
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.core.util.withPluginClassLoader
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.producer.models.AcksType
import io.confluent.intellijplugin.producer.models.Mode
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.producer.models.RecordCompression
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.telemetry.TelemetryUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.util.csv.KafkaCsvUtils
import io.confluent.intellijplugin.util.generator.FieldTemplateGenerator
import io.confluent.intellijplugin.util.generator.GenerateRandomData
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.context.NullContextNameStrategy
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.jetbrains.annotations.VisibleForTesting
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class KafkaProducerClient(
    val dataManager: KafkaDataManager,
    val onStart: () -> Unit,
    val onStop: () -> Unit
) : ProducerClient {
    val client = dataManager.client
    val connectionData = client.connectionData

    @VisibleForTesting
    internal val running = AtomicBoolean(false)

    /** How long to wait for a broker acknowledgement before reporting the send as timed out. */
    @VisibleForTesting
    internal var sendTimeoutMs: Long = DEFAULT_SEND_TIMEOUT_MS

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
        try {
            if (isRunning())
                error("Producer is already run")
            running.set(true)
            onStart()

            val props = createProducerProperties(recordCompression, enableIdempotence, acks)

            @Suppress("UNCHECKED_CAST")
            val producer = withPluginClassLoader {
                val keySerializer: Serializer<out Any> = key.type.getSerializer(dataManager, producerField = key)
                val valueSerializer = value.type.getSerializer(dataManager, producerField = value)
                KafkaProducer(props, keySerializer, valueSerializer) as KafkaProducer<Any, Any>
            }
            try {
                val csvDataFrame = flowParams.csvFile?.let {
                    KafkaCsvUtils.readDataFrame(it)
                }
                val partition = setupPartitions(forcePartition, producer, topic)
                if (!isRunning())
                    return

                produce(producer, topic, partition, key, value, headers, flowParams, csvDataFrame, onUpdate)
            } finally {
                if (isRunning()) {
                    producer.flush()
                }
                producer.close(Duration.ZERO)
            }
        } catch (t: Throwable) {
            RfsNotificationUtils.showExceptionMessage(
                dataManager.project,
                t,
                KafkaMessagesBundle.message("error.producer.title")
            )
        } finally {
            running.set(false)
            onStop()
        }
    }

    @VisibleForTesting
    internal fun produce(
        producer: KafkaProducer<Any, Any>,
        topic: String,
        partition: Int?,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        flowParams: ProducerFlowParams,
        csvDf: DataFrame?,
        onUpdate: (Long, List<KafkaRecord>) -> Unit
    ) {
        when (flowParams.mode) {
            Mode.MANUAL -> sentSeveralMessage(
                flowParams, partition, producer, topic, key, value, headers,
                alreadyProducedCount = 0,
                csvDf = csvDf,
                onUpdate
            )

            Mode.AUTO -> {
                val start = System.currentTimeMillis()
                val totalElapsedTime = flowParams.totalElapsedTime
                val totalRequests = flowParams.totalRequests
                // Only acknowledged sends count toward the "Records produced" stop condition.
                var produced = 0
                // Every attempted send advances the CSV row, so a timed-out row is not re-sent.
                var attempted = 0
                var consecutiveTimeouts = 0
                while (true) {
                    if (!isRunning())
                        return
                    if (totalRequests != 0 && produced >= totalRequests)
                        return
                    if (totalElapsedTime != 0 && (System.currentTimeMillis() - start) >= totalElapsedTime)
                        return

                    val batch = sentSeveralMessage(
                        flowParams, partition, producer, topic, key, value, headers,
                        alreadyProducedCount = attempted,
                        csvDf = csvDf,
                        onUpdate
                    )
                    produced += batch.sent
                    attempted += batch.attempted
                    if (batch.sent > 0) consecutiveTimeouts = 0
                    if (batch.timedOut) consecutiveTimeouts++

                    if (consecutiveTimeouts >= MAX_CONSECUTIVE_SEND_TIMEOUTS) {
                        val error = TimeoutException(
                            KafkaMessagesBundle.message(
                                "producer.auto.stopped.consecutive.timeouts",
                                MAX_CONSECUTIVE_SEND_TIMEOUTS
                            )
                        )
                        onUpdate(0, listOf(createErrorRecord(key, value, topic, partition, error, duration = 0)))
                        // Skip the final flush: it would block on the unacknowledged records.
                        running.set(false)
                        return
                    }
                    Thread.sleep(flowParams.requestInterval.toLong())
                }
            }
        }
    }

    private class BatchResult(val sent: Int, val timedOut: Boolean) {
        val attempted: Int get() = sent + if (timedOut) 1 else 0
    }

    private fun sentSeveralMessage(
        flowParams: ProducerFlowParams,
        partition: Int?,
        producer: KafkaProducer<Any, Any>,
        topic: String,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        alreadyProducedCount: Int,
        csvDf: DataFrame?,
        onUpdate: (Long, List<KafkaRecord>) -> Unit
    ): BatchResult {
        var sent = 0
        for (i in 0 until flowParams.flowRecordsCountPerRequest) {
            if (!isRunning()) break
            val record = sentMessage(
                flowParams, partition, producer, topic, key, value, headers.map { it.copy() },
                alreadyProducedCount = alreadyProducedCount + i,
                csvDf = csvDf
            )
            onUpdate(record.duration, listOf(record))
            if (record.error != null) return BatchResult(sent, timedOut = true)
            sent++
        }
        return BatchResult(sent, timedOut = false)
    }

    private fun setupPartitions(
        forcePartition: Int,
        producer: KafkaProducer<Any, Any>,
        topic: String
    ): Int? {
        val partition = if (forcePartition >= 0) {
            val partitions = producer.partitionsFor(topic)
            if (!partitions.any { it.partition() == forcePartition }) {
                error(KafkaMessagesBundle.message("producer.wrong.partition", forcePartition, topic))
            }
            forcePartition
        } else
            null
        return partition
    }

    private fun createProducerProperties(
        recordCompression: RecordCompression,
        enableIdempotence: Boolean,
        acks: AcksType
    ): Properties {
        val props = client.kafkaProps.clone() as Properties


        props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = recordCompression.name.lowercase()
        props[AbstractKafkaSchemaSerDeConfig.CONTEXT_NAME_STRATEGY] = NullContextNameStrategy::class.java
        props[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false
        if (!props.containsKey(ProducerConfig.CLIENT_ID_CONFIG)) {
            props[ProducerConfig.CLIENT_ID_CONFIG] = "Confluent for IntelliJ ${TelemetryUtils.getPluginVersion()} - Producer"
        }

        when (connectionData.registryType) {
            KafkaRegistryType.NONE -> {}
            KafkaRegistryType.CONFLUENT -> {
                connectionData.registryUrl?.let {
                    props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = it
                }
            }

            KafkaRegistryType.AWS_GLUE -> {}
        }

        if (enableIdempotence)
            props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        else
            props[ProducerConfig.ACKS_CONFIG] = acks.value.toString()
        return props
    }

    override fun stop() {
        running.set(false)
    }

    override fun dispose() = stop()


    private fun sentMessage(
        flowParams: ProducerFlowParams,
        partition: Int?,
        producer: KafkaProducer<Any, Any>,
        topic: String,
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        headers: List<Property>,
        csvDf: DataFrame?,
        alreadyProducedCount: Int,
    ): KafkaRecord {
        val correctKey = when {
            csvDf != null -> key.copy(valueText = KafkaCsvUtils.getKey(csvDf, alreadyProducedCount))
            flowParams.generateRandomKeys -> key.copy(valueText = GenerateRandomData.generate(client.project, key))
            else -> key
        }.let {
            val generated = FieldTemplateGenerator.processTemplate(it.valueText)
            it.copy(valueText = generated)
        }

        val correctValue = when {
            csvDf != null -> value.copy(valueText = KafkaCsvUtils.getValue(csvDf, alreadyProducedCount))
            flowParams.generateRandomValues -> value.copy(
                valueText = GenerateRandomData.generate(
                    client.project,
                    value
                )
            )

            else -> value
        }.let {
            val generated = FieldTemplateGenerator.processTemplate(it.valueText)
            it.copy(valueText = generated)
        }

        val record = ProducerRecord(topic, partition, correctKey.getValueObj(), correctValue.getValueObj())
        val formedHeaders = headers.map {
            val headerKey = FieldTemplateGenerator.processTemplate(it.name ?: "")
            val headerValue = FieldTemplateGenerator.processTemplate(it.value ?: "")
            record.headers().add(headerKey, headerValue.toByteArray())
            Property(headerKey, headerValue)
        }

        val start = System.currentTimeMillis()

        @Suppress("UNCHECKED_CAST")
        val metadataFuture = producer.send(record as ProducerRecord<Any, Any>)
        // Always wait for the in-flight send to complete — stop only prevents new sends
        val metaInfo = try {
            metadataFuture.get(sendTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // The producer keeps retrying in the background (cancelling the future does not stop it),
            // so the record can still land up to delivery.timeout.ms later — say so instead of failing silently.
            thisLogger().info("Producer send to $topic timed out after $sendTimeoutMs ms")
            val error = TimeoutException(
                KafkaMessagesBundle.message("producer.send.timeout.error", sendTimeoutMs.toString())
            )
            return createErrorRecord(
                correctKey, correctValue, topic, partition, error,
                duration = System.currentTimeMillis() - start
            )
        }
        val end = System.currentTimeMillis()
        return KafkaRecord.createFor(
            keyConfig = correctKey, valueConfig = correctValue,
            metadata = metaInfo, duration = (end - start),
            headers = formedHeaders
        )
    }

    private fun createErrorRecord(
        key: ConsumerProducerFieldConfig,
        value: ConsumerProducerFieldConfig,
        topic: String,
        partition: Int?,
        error: Throwable,
        duration: Long
    ): KafkaRecord = KafkaRecord.createFor(
        key.type, value.type, key.schemaFormat, value.schemaFormat,
        Result.failure(error),
        errorPartition = partition
    ).copy(topic = topic, duration = duration)

    companion object {
        private const val DEFAULT_SEND_TIMEOUT_MS = 15_000L

        @VisibleForTesting
        internal const val MAX_CONSECUTIVE_SEND_TIMEOUTS = 3
    }
}