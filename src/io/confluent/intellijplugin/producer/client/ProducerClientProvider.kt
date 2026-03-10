package io.confluent.intellijplugin.producer.client

import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.data.KafkaDataManager

/**
 * Factory for creating producer clients based on connection type.
 *
 * Selects between:
 * - [KafkaProducerClient] for native Kafka connections (KafkaDataManager)
 * - [CCloudProducerClient] for Confluent Cloud connections (CCloudClusterDataManager)
 */
object ProducerClientProvider {

    /**
     * Creates a producer client based on the data manager type.
     *
     * @param dataManager The cluster data manager (either KafkaDataManager or CCloudClusterDataManager)
     * @param onStart Callback invoked when production starts
     * @param onStop Callback invoked when production stops
     * @return A [ProducerClient] appropriate for the connection type
     */
    fun getClient(
        dataManager: BaseClusterDataManager,
        onStart: () -> Unit,
        onStop: () -> Unit
    ): ProducerClient {
        return when (dataManager) {
            is KafkaDataManager -> KafkaProducerClient(
                dataManager = dataManager,
                onStart = onStart,
                onStop = onStop
            )
            is CCloudClusterDataManager -> CCloudProducerClient(
                clusterDataManager = dataManager,
                onStart = onStart,
                onStop = onStop
            )
            else -> throw IllegalArgumentException("Unsupported data manager type: ${dataManager::class.simpleName}")
        }
    }
}
