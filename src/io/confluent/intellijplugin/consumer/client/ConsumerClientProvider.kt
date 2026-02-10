package io.confluent.intellijplugin.consumer.client

import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.data.KafkaDataManager

/**
 * Factory for creating consumer clients based on connection type.
 *
 * Selects between:
 * - [KafkaConsumerClient] for native Kafka connections (KafkaDataManager)
 * - [CCloudConsumerClient] for Confluent Cloud connections (CCloudClusterDataManager)
 */
object ConsumerClientProvider {

    /**
     * Creates a consumer client based on the data manager type.
     *
     * @param dataManager The cluster data manager (either KafkaDataManager or CCloudClusterDataManager)
     * @param onStart Callback invoked when consumption starts
     * @param onStop Callback invoked when consumption stops
     * @return A [ConsumerClient] appropriate for the connection type
     */
    fun getClient(
        dataManager: BaseClusterDataManager,
        onStart: () -> Unit,
        onStop: () -> Unit
    ): ConsumerClient {
        return when (dataManager) {
            is KafkaDataManager -> KafkaConsumerClient(
                dataManager = dataManager,
                onStart = onStart,
                onStop = onStop
            )
            is CCloudClusterDataManager -> CCloudConsumerClient(
                clusterDataManager = dataManager,
                onStart = onStart,
                onStop = onStop,
            )
            else -> throw IllegalArgumentException("Unsupported data manager type: ${dataManager::class.simpleName}")
        }
    }
}
