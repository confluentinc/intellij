package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.withPluginClassLoader
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource
import java.time.Duration

/**
 * Wrapper around AdminClient that ensures all operations are executed with the plugin's classloader
 * to prevent SLF4J classloader conflicts.
 */
class BdtKafkaAdminClient(private val adminClient: AdminClient) : Admin by adminClient, Disposable {
  override fun dispose() = executeOnPooledThread {
    try {
      withPluginClassLoader {
        adminClient.close(Duration.ofSeconds(10))
      }
    }
    catch (t: Throwable) {
      logger.warn("Cannot close kafka client", t)
    }
  }

  // Override the most commonly used methods to wrap them with withPluginClassLoader

  override fun close() = withPluginClassLoader { adminClient.close() }

  override fun createTopics(newTopics: Collection<NewTopic>): CreateTopicsResult = 
    withPluginClassLoader { adminClient.createTopics(newTopics) }

  override fun deleteTopics(topics: Collection<String>): DeleteTopicsResult = 
    withPluginClassLoader { adminClient.deleteTopics(topics) }

  override fun listTopics(): ListTopicsResult = 
    withPluginClassLoader { adminClient.listTopics() }

  override fun listTopics(options: ListTopicsOptions): ListTopicsResult = 
    withPluginClassLoader { adminClient.listTopics(options) }

  override fun describeTopics(topicNames: Collection<String>): DescribeTopicsResult = 
    withPluginClassLoader { adminClient.describeTopics(topicNames) }

  override fun describeCluster(): DescribeClusterResult = 
    withPluginClassLoader { adminClient.describeCluster() }

  override fun describeCluster(options: DescribeClusterOptions): DescribeClusterResult = 
    withPluginClassLoader { adminClient.describeCluster(options) }

  override fun describeConfigs(resources: Collection<ConfigResource>): DescribeConfigsResult = 
    withPluginClassLoader { adminClient.describeConfigs(resources) }

  override fun deleteRecords(recordsToDelete: Map<TopicPartition, RecordsToDelete>): DeleteRecordsResult = 
    withPluginClassLoader { adminClient.deleteRecords(recordsToDelete) }

  override fun describeConsumerGroups(groupIds: Collection<String>): DescribeConsumerGroupsResult = 
    withPluginClassLoader { adminClient.describeConsumerGroups(groupIds) }

  override fun listConsumerGroups(): ListConsumerGroupsResult = 
    withPluginClassLoader { adminClient.listConsumerGroups() }

  override fun listConsumerGroupOffsets(groupId: String): ListConsumerGroupOffsetsResult = 
    withPluginClassLoader { adminClient.listConsumerGroupOffsets(groupId) }

  override fun deleteConsumerGroups(groupIds: Collection<String>): DeleteConsumerGroupsResult = 
    withPluginClassLoader { adminClient.deleteConsumerGroups(groupIds) }

  override fun listOffsets(topicPartitionOffsets: Map<TopicPartition, OffsetSpec>): ListOffsetsResult = 
    withPluginClassLoader { adminClient.listOffsets(topicPartitionOffsets) }

  companion object {
    private val logger = Logger.getInstance(this::class.java)
  }
}
