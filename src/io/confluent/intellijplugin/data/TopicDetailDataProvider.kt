package io.confluent.intellijplugin.data

import io.confluent.intellijplugin.core.monitoring.data.storage.ObjectDataModelStorage
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.model.TopicConfig
import io.confluent.intellijplugin.registry.KafkaRegistryType

/**
 * Interface for data managers that provide topic detail operations.
 * Enables TopicDetailsController, TopicPartitionsController, and TopicConfigsController
 * to work with both KafkaDataManager and ClusterScopedDataManager.
 */
interface TopicDetailDataProvider {
    val topicPartitionsModels: ObjectDataModelStorage<String, BdtTopicPartition>
    val topicConfigsModels: ObjectDataModelStorage<String, TopicConfig>
    val updater: BdtMonitoringUpdater
    val registryType: KafkaRegistryType

    fun clearPartitions(partitions: List<BdtTopicPartition>)

    /**
     * Returns true if this connection supports clearing partitions.
     * Regular Kafka connections support this, but Confluent Cloud REST API does not.
     */
    fun supportsClearPartitions(): Boolean = true
}
