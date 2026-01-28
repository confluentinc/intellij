package io.confluent.intellijplugin.producer.client

import com.intellij.charts.dataframe.DataFrame
import io.confluent.intellijplugin.client.KafkaClient
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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class KafkaProducerClient(val client: KafkaClient) {
    val connectionData = client.connectionData

    val isRunning = AtomicBoolean(false)

    fun isRunning(): Boolean = isRunning.get()

    fun start(
        dataManager: KafkaDataManager,
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
            val props = createProducerProperties(recordCompression, enableIdempotence, acks)

            @Suppress("UNCHECKED_CAST")
            val producer = withPluginClassLoader {
                val keySerializer: Serializer<out Any> = key.type.getSerializer(dataManager, producerField = key)
                val valueSerializer = value.type.getSerializer(dataManager, producerField = value)
                KafkaProducer(props, keySerializer, valueSerializer) as KafkaProducer<Any, Any>
            }
            try {
                isRunning.set(true)

                val csvDataFrame = flowParams.csvFile?.let {
                    KafkaCsvUtils.readDataFrame(it)
                }
                val partition = setupPartitions(forcePartition, producer, topic)
                if (!isRunning())
                    return

                var produced = 0
                when (flowParams.mode) {
                    Mode.MANUAL -> sentSeveralMessage(
                        flowParams, partition, producer, topic, key, value, headers,
                        alreadyProducedCount = produced,
                        csvDf = csvDataFrame,
                        onUpdate
                    )

                    Mode.AUTO -> {
                        val start = System.currentTimeMillis()
                        val totalElapsedTime = flowParams.totalElapsedTime
                        val totalRequests = flowParams.totalRequests
                        while (true) {
                            if (!isRunning())
                                return
                            if (totalRequests != 0 && produced >= totalRequests)
                                return
                            if (totalElapsedTime != 0 && (System.currentTimeMillis() - start) >= totalElapsedTime)
                                return

                            sentSeveralMessage(
                                flowParams, partition, producer, topic, key, value, headers,
                                alreadyProducedCount = produced,
                                csvDf = csvDataFrame,
                                onUpdate
                            )
                            produced += flowParams.flowRecordsCountPerRequest
                            Thread.sleep(flowParams.requestInterval.toLong())
                        }
                    }
                }
            } finally {
                producer.flush()
                producer.close()
                isRunning.set(false)
            }
        } catch (t: Throwable) {
            RfsNotificationUtils.showExceptionMessage(
                dataManager.project,
                t,
                KafkaMessagesBundle.message("error.producer.title")
            )
        }
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
    ) {
        val startTime = System.currentTimeMillis()
        val produced = mutableListOf<KafkaRecord>()
        repeat(flowParams.flowRecordsCountPerRequest) {
            if (!isRunning())
                return
            val result = sentMessage(
                flowParams, partition, producer, topic, key, value, headers.map { it.copy() },
                alreadyProducedCount = alreadyProducedCount + it,
                csvDf = csvDf
            ) ?: return
            produced.add(result)
        }
        val endTime = System.currentTimeMillis()
        onUpdate(endTime - startTime, produced)
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
        if (!props.containsKey(ConsumerConfig.CLIENT_ID_CONFIG)) {
            props[ConsumerConfig.CLIENT_ID_CONFIG] = "Confluent for IntelliJ ${TelemetryUtils.getPluginVersion()} - Producer"
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

    fun stop() {
        if (!isRunning())
            error("Producer is not run")
        isRunning.set(false)
    }


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
    ): KafkaRecord? {
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
        val sendTimeout = 15000
        while (System.currentTimeMillis() - start < sendTimeout) {
            Thread.sleep(100)
            if (metadataFuture.isDone)
                break
            if (!isRunning()) {
                metadataFuture.cancel(true)
                break
            }
        }

        if (!isRunning())
            return null
        val metaInfo = metadataFuture.get(2, TimeUnit.SECONDS)
        val end = System.currentTimeMillis()

        return KafkaRecord.createFor(
            keyConfig = correctKey, valueConfig = correctValue,
            metadata = metaInfo, duration = (end - start),
            headers = formedHeaders
        )
    }
}