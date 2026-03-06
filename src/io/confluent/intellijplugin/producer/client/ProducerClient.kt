package io.confluent.intellijplugin.producer.client

import com.intellij.openapi.Disposable
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.producer.models.AcksType
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.producer.models.RecordCompression

/**
 * Interface for producing records to Kafka.
 *
 * Implementations may use different protocols:
 * - [KafkaProducerClient]: Native Kafka protocol
 * - [CCloudProducerClient]: Confluent Cloud REST API
 *
 * The implementation is selected based on connection type via [ProducerClientProvider].
 */
interface ProducerClient : Disposable {

    fun start(
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
    )

    fun stop()

    fun isRunning(): Boolean
}
