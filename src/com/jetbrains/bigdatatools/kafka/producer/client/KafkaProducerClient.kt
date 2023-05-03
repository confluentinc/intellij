package com.jetbrains.bigdatatools.kafka.producer.client

import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.common.util.withPluginClassLoader
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.models.*
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.generator.GenerateRandomData
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.context.NullContextNameStrategy
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

  fun start(dataManager: KafkaDataManager,
            topic: String,
            key: ConsumerProducerFieldConfig,
            value: ConsumerProducerFieldConfig,
            headers: List<Property>,
            recordCompression: RecordCompression,
            acks: AcksType,
            enableIdempotence: Boolean,
            forcePartition: Int,
            flowParams: ProducerFlowParams,
            onUpdate: (ProducerResultMessage) -> Unit) {
    try {
      if (isRunning())
        error("Producer is already run")
      val props = createProducerProperties(recordCompression, enableIdempotence, acks)

      val keySerializer: Serializer<out Any> = key.type.getSerializer(dataManager, producerField = key)
      val valueSerializer = value.type.getSerializer(dataManager, producerField = value)

      @Suppress("UNCHECKED_CAST")
      val producer = withPluginClassLoader {
        KafkaProducer(props, keySerializer, valueSerializer) as KafkaProducer<Any, Any>
      }
      try {
        isRunning.set(true)
        val partition = setupPartitions(forcePartition, producer, topic)
        if (!isRunning())
          return

        when (flowParams.mode) {
          Mode.MANUAL -> sentSeveralMessage(flowParams, partition, producer, dataManager, topic, key, value, headers, onUpdate)
          Mode.AUTO -> {
            val start = System.currentTimeMillis()
            var produced = 0
            val totalElapsedTime = flowParams.totalElapsedTime
            val totalRequests = flowParams.totalRequests
            while (true) {
              if (!isRunning())
                return
              if (totalRequests != 0 && produced >= totalRequests)
                return
              if (totalElapsedTime != 0 && (System.currentTimeMillis() - start) >= totalElapsedTime)
                return

              sentSeveralMessage(flowParams, partition, producer, dataManager, topic, key, value, headers, onUpdate)
              produced += flowParams.flowRecordsCountPerRequest
              Thread.sleep(flowParams.requestInterval.toLong())
            }
          }
        }
      }
      finally {
        producer.flush()
        producer.close()
        isRunning.set(false)
      }
    }
    catch (t: Throwable) {
      RfsNotificationUtils.showExceptionMessage(dataManager.project, t, KafkaMessagesBundle.message("error.producer.title"))
    }
  }

  private fun sentSeveralMessage(flowParams: ProducerFlowParams,
                                 partition: Int?,
                                 producer: KafkaProducer<Any, Any>,
                                 dataManager: KafkaDataManager,
                                 topic: String,
                                 key: ConsumerProducerFieldConfig,
                                 value: ConsumerProducerFieldConfig,
                                 headers: List<Property>,
                                 onUpdate: (ProducerResultMessage) -> Unit) {
    repeat(flowParams.flowRecordsCountPerRequest) {
      if (!isRunning())
        return
      val result = sentMessage(flowParams, partition, producer, dataManager, topic, key, value, headers) ?: return
      onUpdate(result)
    }
  }

  private fun setupPartitions(forcePartition: Int,
                              producer: KafkaProducer<Any, Any>,
                              topic: String): Int? {
    val partition = if (forcePartition >= 0) {
      val partitions = producer.partitionsFor(topic)
      if (!partitions.any { it.partition() == forcePartition }) {
        error(KafkaMessagesBundle.message("producer.wrong.partition", forcePartition, topic))
      }
      forcePartition
    }
    else
      null
    return partition
  }

  private fun createProducerProperties(recordCompression: RecordCompression,
                                       enableIdempotence: Boolean,
                                       acks: AcksType): Properties {
    val props = client.kafkaProps.clone() as Properties


    props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = recordCompression.name.lowercase()
    props[AbstractKafkaSchemaSerDeConfig.CONTEXT_NAME_STRATEGY] = NullContextNameStrategy::class.java
    props[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false

    when (connectionData.registryType) {
      KafkaRegistryType.NONE -> {}
      KafkaRegistryType.CONFLUENT -> {
        connectionData.registryUrl?.let { props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = it }
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


  private fun sentMessage(flowParams: ProducerFlowParams,
                          partition: Int?,
                          producer: KafkaProducer<Any, Any>,
                          dataManager: KafkaDataManager,
                          topic: String,
                          key: ConsumerProducerFieldConfig,
                          value: ConsumerProducerFieldConfig,
                          headers: List<Property>): ProducerResultMessage? {
    val keyObj = if (flowParams.generateRandomKeys)
      GenerateRandomData.generate(key, dataManager)
    else
      key.getValueObj(dataManager)

    val valueObj = if (flowParams.generateRandomValues)
      GenerateRandomData.generate(value, dataManager)
    else
      value.getValueObj(dataManager)

    val record = ProducerRecord(topic, partition, keyObj, valueObj)
    headers.forEach {
      record.headers().add((it.name ?: ""), (it.value ?: "").toByteArray())
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
    return ProducerResultMessage(key = key.valueText,
                                 value = value.valueText,
                                 offset = metaInfo.offset(),
                                 timestamp = Date(metaInfo.timestamp()),
                                 duration = (end - start).toInt(),
                                 partition = metaInfo.partition())
  }
}