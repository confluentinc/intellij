package io.confluent.intellijplugin.consumer.client

import io.confluent.intellijplugin.data.ClusterScopedDataManager
import io.confluent.intellijplugin.data.KafkaDataManager

/**
 * Factory for creating consumer clients based on connection type.
 *
 * Selects between:
 * - [KafkaConsumerClient] for native Kafka connections (KafkaDataManager)
 * - [CCloudConsumerClient] for Confluent Cloud connections (ClusterScopedDataManager)
 */
object ConsumerClientProvider {

    /**
     * Creates a consumer client for native Kafka connections.
     *
     * @param dataManager The Kafka data manager for native connections
     * @param onStart Callback invoked when consumption starts
     * @param onStop Callback invoked when consumption stops
     * @return A [KafkaConsumerClient] for native Kafka protocol
     */
    fun getClient(
        dataManager: KafkaDataManager,
        onStart: () -> Unit,
        onStop: () -> Unit
    ): ConsumerClient {
        return KafkaConsumerClient(
            dataManager = dataManager,
            onStart = onStart,
            onStop = onStop
        )
    }

    /**
     * Creates a consumer client for Confluent Cloud connections.
     *
     * @param dataManager The cluster-scoped data manager for CCloud connections
     * @param onStart Callback invoked when consumption starts
     * @param onStop Callback invoked when consumption stops
     * @return A [CCloudConsumerClient] for REST-based consumption
     */
    fun getClient(
        dataManager: ClusterScopedDataManager,
        onStart: () -> Unit,
        onStop: () -> Unit
    ): ConsumerClient {
        return CCloudConsumerClient(
            clusterDataManager = dataManager,
            onStart = onStart,
            onStop = onStop,
            parentScope = dataManager.driver.coroutineScope
        )
    }
}
