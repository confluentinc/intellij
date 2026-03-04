package io.confluent.intellijplugin.common.settings

import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.models.*
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KafkaConfigStorageTest {

    private lateinit var storage: KafkaConfigStorage

    @BeforeEach
    fun setUp() {
        storage = KafkaConfigStorage()
    }

    private fun makeConfig(connectionType: String?) = StorageConsumerConfig(
        topic = "test-topic",
        keyType = KafkaFieldType.STRING,
        valueType = KafkaFieldType.STRING,
        filter = ConsumerFilter(null, null, null, null, ConsumerFilterType.NONE),
        limit = ConsumerLimit(ConsumerLimitType.NONE, "", null),
        partitions = "",
        startWith = ConsumerStartWith(ConsumerStartType.NOW, null, null, null),
        properties = emptyMap(),
        settings = emptyMap(),
        keyFormat = KafkaRegistryFormat.JSON,
        valueFormat = KafkaRegistryFormat.JSON,
        customKeySchema = null,
        customValueSchema = null,
    ).copy(connectionType = connectionType)

    @Nested
    @DisplayName("consumerConfigFor")
    inner class ConsumerConfigFor {

        @Test
        fun `should show only null-typed presets for native connection`() {
            val native = makeConfig(null)
            val ccloud = makeConfig("ccloud")
            storage.consumerConfig.addConfig(native)
            storage.consumerConfig.addConfig(ccloud)

            val nativeView = storage.consumerConfigFor(null)

            assertEquals(listOf(native), nativeView.loadConfigs())
        }

        @Test
        fun `should show only ccloud-typed presets for ccloud connection`() {
            val native = makeConfig(null)
            val ccloud = makeConfig("ccloud")
            storage.consumerConfig.addConfig(native)
            storage.consumerConfig.addConfig(ccloud)

            val ccloudView = storage.consumerConfigFor("ccloud")

            assertEquals(listOf(ccloud), ccloudView.loadConfigs())
        }

        @Test
        fun `should share underlying storage across scoped views`() {
            val ccloudView = storage.consumerConfigFor("ccloud")
            val nativeView = storage.consumerConfigFor(null)
            val ccloud = makeConfig("ccloud")

            ccloudView.addConfig(ccloud)

            assertEquals(listOf(ccloud), ccloudView.loadConfigs())
            assertTrue(nativeView.loadConfigs().isEmpty())
            assertEquals(1, storage.consumerRunConfigs.size)
        }

        @Test
        fun `should notify listeners in all scoped views when a config is added`() {
            val ccloudAdditions = mutableListOf<StorageConfig>()
            val nativeAdditions = mutableListOf<StorageConfig>()

            val ccloudView = storage.consumerConfigFor("ccloud").also {
                it.addChangeListener(object : ConfigChangeListener<StorageConfig> {
                    override fun configAdded(config: StorageConfig) { ccloudAdditions.add(config) }
                    override fun configRemoved(config: StorageConfig) {}
                })
            }
            storage.consumerConfigFor(null).also {
                it.addChangeListener(object : ConfigChangeListener<StorageConfig> {
                    override fun configAdded(config: StorageConfig) { nativeAdditions.add(config) }
                    override fun configRemoved(config: StorageConfig) {}
                })
            }

            val ccloud = makeConfig("ccloud")
            ccloudView.addConfig(ccloud)

            assertEquals(listOf(ccloud), ccloudAdditions)
            assertEquals(listOf(ccloud), nativeAdditions)
        }
    }
}