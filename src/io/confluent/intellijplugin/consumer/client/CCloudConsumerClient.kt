package io.confluent.intellijplugin.consumer.client

import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.data.KafkaDataManager
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REST-based consumer client for Confluent Cloud.
 *
 * Uses Confluent Cloud REST API with OAuth authentication
 * to consume records from Kafka topics.
 *
 * @param dataManager The Kafka data manager containing connection information
 * @param onStart Callback invoked when consumption starts
 * @param onStop Callback invoked when consumption stops
 */
class CCloudConsumerClient(
    val dataManager: KafkaDataManager,
    val onStart: () -> Unit,
    val onStop: () -> Unit
) : ConsumerClient {

    private val running = AtomicBoolean(false)

    override fun start(
        config: StorageConsumerConfig,
        dataManager: KafkaDataManager,
        valueConfig: ConsumerProducerFieldConfig,
        keyConfig: ConsumerProducerFieldConfig,
        consume: (Long, List<ConsumerRecord<Any, Any>>) -> Unit,
        timestampUpdate: () -> Unit,
        consumeError: (Throwable, Int?, Long?) -> Unit
    ) {
        // TODO
        // Call REST API to consume records
        // Poll loop with coroutines
        // Apply client-side filtering and limits
        // Convert REST response to ConsumerRecord format
        throw UnsupportedOperationException("TODO")
    }

    override fun stop() {
        running.set(false)
        onStop()
    }

    override fun isRunning(): Boolean = running.get()

    override fun dispose() {
        stop()
    }
}
