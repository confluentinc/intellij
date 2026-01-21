package io.confluent.intellijplugin.consumer.client

import io.confluent.intellijplugin.data.ClusterScopedDataManager
import io.confluent.intellijplugin.data.KafkaDataManager

/**
 * Factory for creating consumer clients based on connection type.
 *
 * Selects between:
 * - [KafkaConsumerClient] for native Kafka connections
 * - [CCloudConsumerClient] for Confluent Cloud connections
 */
object ConsumerClientProvider {

    /**
     * Creates a consumer client appropriate for the given data manager.
     *
     * @param dataManager The data manager for the connection. Type determines client selection:
     * @param onStart Callback invoked when consumption starts
     * @param onStop Callback invoked when consumption stops
     * @return A [ConsumerClient] implementation
     */
    fun getClient(
        dataManager: KafkaDataManager,
        onStart: () -> Unit,
        onStop: () -> Unit
    ): ConsumerClient {
        // TODO will add ClusterScopedDataManager support via overloaded method
        return KafkaConsumerClient(
            dataManager = dataManager,
            onStart = onStart,
            onStop = onStop
        )
    }

    /* Creates a consumer client for CCloud cluster-scoped connections. For now, this method exists to establish the pattern */
    fun getClient(
        dataManager: ClusterScopedDataManager,
        onStart: () -> Unit,
        onStop: () -> Unit
    ): ConsumerClient {
        // TODO: CCloudConsumerClient needs ClusterScopedDataManager
        throw UnsupportedOperationException(
            "TODO"
        )
    }
}
