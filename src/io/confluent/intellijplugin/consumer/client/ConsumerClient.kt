package io.confluent.intellijplugin.consumer.client

import com.intellij.openapi.Disposable
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.data.KafkaDataManager
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
 * Interface for consuming records from Kafka.
 *
 * Implementations may use different protocols:
 * - [KafkaConsumerClient]: Native Kafka protocol
 * - [CCloudConsumerClient]: Confluent Cloud REST API
 *
 * The implementation is selected based on connection type via [ConsumerClientProvider].
 */
interface ConsumerClient : Disposable {

    /**
     * Start consuming records from the configured topic.
     *
     * @param config Consumer configuration (topic, partitions, start position, limits, filters)
     * @param dataManager The Kafka data manager for the connection
     * @param valueConfig Value deserialization configuration
     * @param keyConfig Key deserialization configuration
     * @param consume Callback for each batch of consumed records (pollTime in ms, records)
     * @param timestampUpdate Callback for progress/timestamp updates
     * @param consumeError Callback for consumption errors (exception, partition, offset)
     */
    fun start(
        config: StorageConsumerConfig,
        dataManager: KafkaDataManager,
        valueConfig: ConsumerProducerFieldConfig,
        keyConfig: ConsumerProducerFieldConfig,
        consume: (Long, List<ConsumerRecord<Any, Any>>) -> Unit,
        timestampUpdate: () -> Unit,
        consumeError: (Throwable, Int?, Long?) -> Unit
    )

    /**
     * Stop consuming records.
     */
    fun stop()

    /**
     * Check if consumer is currently running.
     */
    fun isRunning(): Boolean
}
