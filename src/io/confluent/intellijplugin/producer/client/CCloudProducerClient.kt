package io.confluent.intellijplugin.producer.client

import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.producer.models.AcksType
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.producer.models.RecordCompression
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
        TODO()
    }

    override fun stop() {
        running.set(false)
    }

    override fun dispose() {
        stop()
    }
}
