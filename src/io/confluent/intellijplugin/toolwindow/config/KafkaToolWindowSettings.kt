package io.confluent.intellijplugin.toolwindow.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.settings.ColumnVisibilitySettings
import io.confluent.intellijplugin.model.*
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo

@State(name = "ConfluentIntellijKafkaToolWindowSettings", storages = [Storage("confluent_kafka_toolwindow.xml")])
class KafkaToolWindowSettings : PersistentStateComponent<KafkaToolWindowSettings>, IntervalUpdateSettings {
    var showFullTopicConfig: Boolean = false
    override var selectedConnectionId: String? = null

    private val topicConfigsTableColumns = mutableListOf(
        TopicConfig::name.name,
        TopicConfig::value.name,
        TopicConfig::defaultValue.name
    )
    val topicConfigsColumnSettings = ColumnVisibilitySettings(topicConfigsTableColumns)

    private val topicPartitionsTableColumns = mutableListOf(
        BdtTopicPartition::partitionId.name,
        BdtTopicPartition::messageCount.name,
        BdtTopicPartition::startOffset.name,
        BdtTopicPartition::endOffset.name,
        BdtTopicPartition::leader.name,
        BdtTopicPartition::replicas.name,
    )

    val topicPartitionsColumnSettings = ColumnVisibilitySettings(topicPartitionsTableColumns)

    private val kafkaTopicTableColumns = mutableListOf(
        TopicPresentable::isFavorite.name,
        TopicPresentable::name.name,
        TopicPresentable::messageCount.name,
        TopicPresentable::partitions.name,
        TopicPresentable::replicationFactor.name,
        TopicPresentable::inSyncReplicas.name
    )
    val kafkaTopicColumnSettings = ColumnVisibilitySettings(kafkaTopicTableColumns)

    private val ccloudTopicTableColumns = mutableListOf(
        TopicPresentable::isFavorite.name,
        TopicPresentable::name.name,
        TopicPresentable::messageCount.name,
        TopicPresentable::partitions.name,
        TopicPresentable::replicationFactor.name
    )
    val ccloudTopicColumnSettings = ColumnVisibilitySettings(ccloudTopicTableColumns)

    var showInternalTopics: Boolean = false
    var showFavoriteTopics: Boolean = false
    var showFavoriteSchema: Boolean = false

    private val consumerGroupsTableColumns = mutableListOf(
        ConsumerGroupPresentable::isFavorite.name,
        ConsumerGroupPresentable::consumerGroup.name,
        ConsumerGroupPresentable::state.name
    )
    val consumerGroupsColumnSettings = ColumnVisibilitySettings(consumerGroupsTableColumns)

    private val consumerGroupOffsetTableColumns = mutableListOf(
        ConsumerGroupOffsetInfo::topic.name,
        ConsumerGroupOffsetInfo::partition.name,
        ConsumerGroupOffsetInfo::lag.name,
        ConsumerGroupOffsetInfo::offset.name
    )
    val consumerGroupOffsetColumnSettings = ColumnVisibilitySettings(consumerGroupOffsetTableColumns)

    private val confluentSchemaTableColumns = mutableListOf(
        KafkaSchemaInfo::isFavorite.name,
        KafkaSchemaInfo::name.name,
        KafkaSchemaInfo::type.name,
        KafkaSchemaInfo::version.name
    )

    val confluentSchemaTableColumnSettings = ColumnVisibilitySettings(confluentSchemaTableColumns)

    private val glueSchemaTableColumns = confluentSchemaTableColumns + mutableListOf(
        KafkaSchemaInfo::compatibility.name,
        KafkaSchemaInfo::updatedTime.name,
        KafkaSchemaInfo::description.name,
    )

    val glueSchemaTableColumnSettings = ColumnVisibilitySettings(glueSchemaTableColumns.toMutableList())

    override var configs: MutableMap<String, KafkaClusterConfig> = mutableMapOf()

    override var dataUpdateIntervalMillis: Int = 0

    fun getOrCreateConfig(connectionId: String): KafkaClusterConfig {
        var config = configs[connectionId]
        if (config == null) {
            config = KafkaClusterConfig()
            configs[connectionId] = config
        }
        return config
    }

    override fun getState(): KafkaToolWindowSettings = this

    override fun loadState(state: KafkaToolWindowSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): KafkaToolWindowSettings = service()
    }
}